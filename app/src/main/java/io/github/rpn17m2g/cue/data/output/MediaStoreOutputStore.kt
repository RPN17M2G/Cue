package io.github.rpn17m2g.cue.data.output

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedSong(
    val uri: String,
    val displayName: String,
    val relativePath: String,
)

interface OutputStore {
    suspend fun save(source: File, displayNameBase: String, outputFolder: String): SavedSong
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
            context.contentResolver.update(uri, publish, null, null)
            SavedSong(uri = uri.toString(), displayName = displayName, relativePath = folder)
        } catch (e: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw e
        }
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
