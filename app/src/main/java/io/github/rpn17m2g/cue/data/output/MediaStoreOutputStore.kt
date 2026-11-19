package io.github.rpn17m2g.cue.data.output

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import io.github.rpn17m2g.cue.domain.model.Artwork
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What happened to the source video, if Review offered to delete it. */
enum class SourceOutcome {
    /** No deletion was requested (MP3 input, or the toggle was off). */
    NONE,

    /** Saved and verified; the Saved screen still has to ask the OS. */
    PENDING,

    DELETED,

    KEPT,
}

data class SavedSong(
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val inPlaceEdit: Boolean = false,
    /** True when an in-place edit also replaced the audio, not just the tags. */
    val trimmedInPlace: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val durationMs: Long? = null,
    val artwork: Artwork? = null,
    /** The original video document, when the job asked to delete it. */
    val sourceUri: String? = null,
    val sourceOutcome: SourceOutcome = SourceOutcome.NONE,
)

interface OutputStore {
    suspend fun save(source: File, displayNameBase: String, outputFolder: String): SavedSong

    /** Copies a published song back to a local file so its tags can be read back. */
    suspend fun copyOut(uri: String, target: File)

    /** Removes a published song, used when read-back verification fails. */
    suspend fun delete(uri: String)

    /**
     * Refreshes the gallery row after the audio of an existing song changed in
     * place, so its duration does not stay at the old value. Best effort: the
     * bytes are already written when this runs, so a failure is cosmetic.
     */
    suspend fun refresh(uri: String, durationMs: Long?) = Unit
}

/**
 * Publishes a finished MP3 through MediaStore so no storage permission is needed.
 * The row stays pending until the bytes are fully copied, hiding partial files
 * from other apps.
 */
class MediaStoreOutputStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OutputStore {

    override suspend fun save(
        source: File,
        displayNameBase: String,
        outputFolder: String,
    ): SavedSong = withContext(ioDispatcher) {
        val folder = Mp3FileNamer.normalizeFolder(outputFolder)
        val existing = existingNames(folder)
        val displayName = Mp3FileNamer.uniqueName(displayNameBase, existing)
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, folder)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: throw IOException("MediaStore refused to create $displayName")
        try {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                if (output == null) throw IOException("MediaStore returned no stream for $displayName")
                source.inputStream().use { input -> input.copyTo(output) }
            }
            val publish = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            val published = context.contentResolver.update(uri, publish, null, null)
            if (published == 0) {
                throw IOException("MediaStore did not publish $displayName")
            }
            SavedSong(uri = uri.toString(), displayName = displayName, relativePath = folder)
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
    }

    override suspend fun copyOut(uri: String, target: File) {
        withContext(ioDispatcher) {
            val stream = context.contentResolver.openInputStream(Uri.parse(uri))
                ?: throw IOException("MediaStore returned no stream for $uri")
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    override suspend fun delete(uri: String) {
        withContext(ioDispatcher) {
            val removed = context.contentResolver.delete(Uri.parse(uri), null, null)
            if (removed == 0) throw IOException("MediaStore did not delete $uri")
        }
    }

    override suspend fun refresh(uri: String, durationMs: Long?) {
        withContext(ioDispatcher) {
            // A picker hands out a documents-provider URI
            // (com.android.providers.media.documents), while the row that holds
            // DURATION is the media one; without the mapping this is a no-op.
            val target = mediaRow(Uri.parse(uri)) ?: return@withContext
            val values = ContentValues().apply {
                durationMs?.takeIf { it > 0 }?.let {
                    put(MediaStore.Audio.Media.DURATION, it)
                }
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1_000)
            }
            runCatching { context.contentResolver.update(target, values, null, null) }
        }
    }

    /** A SAF document picked from Downloads may not be a MediaStore row at all. */
    private fun mediaRow(document: Uri): Uri? = when (document.authority) {
        MediaStore.AUTHORITY -> document
        else -> runCatching { MediaStore.getMediaUri(context, document) }.getOrNull()
    }

    private fun existingNames(folder: String): Set<String> {
        val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        return runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection,
                selection,
                arrayOf(folder),
                null,
            )?.use { cursor ->
                val column = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                buildSet {
                    while (cursor.moveToNext()) {
                        if (column >= 0) cursor.getString(column)?.let(::add)
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptySet())
    }

    private companion object {
        const val MIME_TYPE = "audio/mpeg"
    }
}

/**
 * Chooses a display name that does not collide with [existing]. A collision adds
 * ` (2)`, ` (3)`, and so on before the extension.
 */
object Mp3FileNamer {

    const val MP3_EXTENSION = ".mp3"

    fun normalizeFolder(folder: String): String {
        val cleaned = folder.trim()
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
        return "${cleaned.ifEmpty { DEFAULT_FOLDER }}/"
    }

    fun uniqueName(baseName: String, existing: Collection<String>): String {
        val stem = baseName.trim().ifBlank { FALLBACK_NAME }
        val taken = existing.mapTo(HashSet()) { it.lowercase() }
        val first = "$stem$MP3_EXTENSION"
        if (first.lowercase() !in taken) return first
        var index = 2
        while (true) {
            val candidate = "$stem ($index)$MP3_EXTENSION"
            if (candidate.lowercase() !in taken) return candidate
            index += 1
        }
    }

    private const val DEFAULT_FOLDER = "Music/Cue"
    private const val FALLBACK_NAME = "Unknown track"
}
