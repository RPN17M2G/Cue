package io.github.rpn17m2g.cue.data.output

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * Removes the original recording once its MP3 is saved and verified.
 *
 * MediaStore rows (including the `media.documents` URIs the picker returns for
 * screen recordings) go through [MediaStore.createDeleteRequest], so Android
 * shows its own confirmation and Cue needs no broad write access. A document
 * from any other provider has no system delete dialog, so the Saved screen's
 * in-app confirmation is the only one and [deleteDocument] is used directly.
 */
class SourceDeleter(private val context: Context) {

    /** Returns a system delete request, or `null` when the URI is not a MediaStore row. */
    fun deleteRequest(uri: String): IntentSender? {
        val mediaUri = mediaStoreUri(Uri.parse(uri)) ?: return null
        return runCatching {
            MediaStore.createDeleteRequest(context.contentResolver, listOf(mediaUri)).intentSender
        }.getOrNull()
    }

    /** Deletes a non-MediaStore document directly; `false` when it was not removed. */
    fun deleteDocument(uri: String): Boolean {
        val parsed = Uri.parse(uri)
        if (mediaStoreUri(parsed) != null) return false
        return runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, parsed)
        }.getOrDefault(false)
    }

    /**
     * True while the source is still present. The system dialog reports success
     * as soon as the user allows the deletion; this check confirms the row is
     * actually gone before the Saved screen claims it was deleted. Errors are
     * treated as "still there" so a failed query never hides a kept file.
     */
    fun exists(uri: String): Boolean {
        val mediaUri = mediaStoreUri(Uri.parse(uri)) ?: return true
        return runCatching {
            context.contentResolver.query(
                mediaUri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { it.count > 0 } ?: true
        }.getOrDefault(true)
    }

    /**
     * Maps a picker URI onto the MediaStore item it points at. [MediaStore.getMediaUri]
     * keeps the volume encoded in the document id, so a file on a secondary
     * volume is not confused with the same row id on the primary one.
     */
    private fun mediaStoreUri(uri: Uri): Uri? = when (uri.authority) {
        MediaStore.AUTHORITY -> uri
        MEDIA_DOCUMENTS_AUTHORITY ->
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
        else -> null
    }

    private companion object {
        const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    }
}
