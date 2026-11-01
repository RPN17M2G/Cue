package io.github.rpn17m2g.cue.data.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LibraryKind { VIDEO, AUDIO }

/** Where an entry came from, which is what the row's chip explains. */
enum class LibrarySource { RECORDING, CUE_OUTPUT, MUSIC }

data class LibraryEntry(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val durationMs: Long?,
    val dateAddedSeconds: Long,
    val kind: LibraryKind,
    val source: LibrarySource,
)

data class MediaPermissions(
    val video: Boolean,
    val audio: Boolean,
) {
    val any: Boolean get() = video || audio
    val all: Boolean get() = video && audio
}

/**
 * Reads the two collections the gallery shows: screen recordings from
 * MediaStore's video table and music from the audio table, with Cue's own
 * output folder marked so the Music tab can pin it first.
 *
 * The gallery is a convenience over the system picker, so a failed or denied
 * query returns an empty list rather than breaking Home.
 */
class MediaLibrary(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun permissions(): MediaPermissions = MediaPermissions(
        video = isGranted(videoPermission()),
        audio = isGranted(audioPermission()),
    )

    suspend fun load(): List<LibraryEntry> = withContext(ioDispatcher) {
        val permissions = permissions()
        val videos = if (permissions.video) queryVideos() else emptyList()
        val audio = if (permissions.audio) queryAudio() else emptyList()
        videos + audio
    }

    private fun queryVideos(): List<LibraryEntry> {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
        )
        // RELATIVE_PATH keeps this working for any locale; the display-name
        // prefix also catches recordings that were moved out of DCIM.
        val selection = "(${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?) OR " +
            "(${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?)"
        val args = arrayOf("%Screen recordings%", "Screen_Recording%")
        return query(collection, projection, selection, args) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
            val name = cursor.getStringOrNull(MediaStore.Video.Media.DISPLAY_NAME)
                ?: return@query null
            LibraryEntry(
                uri = ContentUris.withAppendedId(collection, id),
                title = name.substringBeforeLast('.').ifBlank { name },
                artist = null,
                durationMs = cursor.getLongOrNull(MediaStore.Video.Media.DURATION),
                dateAddedSeconds = cursor.getLongOrNull(MediaStore.Video.Media.DATE_ADDED) ?: 0L,
                kind = LibraryKind.VIDEO,
                source = LibrarySource.RECORDING,
            )
        }
    }

    private fun queryAudio(): List<LibraryEntry> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        // IS_MUSIC drops ringtones and voice notes. The folder clause keeps a
        // freshly written Cue file visible even before the scanner has flagged
        // it as music.
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0) OR " +
            "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?)"
        val args = arrayOf("%Music/Cue%")
        return query(collection, projection, selection, args) { cursor ->
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
            val name = cursor.getStringOrNull(MediaStore.Audio.Media.DISPLAY_NAME)
                ?: return@query null
            val title = cursor.getStringOrNull(MediaStore.Audio.Media.TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: name.substringBeforeLast('.').ifBlank { name }
            val relativePath = cursor.getStringOrNull(MediaStore.Audio.Media.RELATIVE_PATH).orEmpty()
            LibraryEntry(
                uri = ContentUris.withAppendedId(collection, id),
                title = title,
                artist = cursor.getStringOrNull(MediaStore.Audio.Media.ARTIST)
                    ?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING },
                durationMs = cursor.getLongOrNull(MediaStore.Audio.Media.DURATION),
                dateAddedSeconds = cursor.getLongOrNull(MediaStore.Audio.Media.DATE_ADDED) ?: 0L,
                kind = LibraryKind.AUDIO,
                source = if (relativePath.startsWith("Music/Cue")) {
                    LibrarySource.CUE_OUTPUT
                } else {
                    LibrarySource.MUSIC
                },
            )
        }
    }

    private fun query(
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        read: (android.database.Cursor) -> LibraryEntry?,
    ): List<LibraryEntry> = runCatching {
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) read(cursor)?.let(::add)
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun videoPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private fun audioPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

/** The permissions Home has to request for the gallery to fill. */
fun requiredMediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getLongOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return if (index < 0 || isNull(index)) null else getLong(index)
}
