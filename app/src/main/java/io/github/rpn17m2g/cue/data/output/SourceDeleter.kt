package io.github.rpn17m2g.cue.data.output

import android.content.ContentUris
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
     * Maps a picker URI onto the MediaStore item it points at. The media
     * documents provider uses `video:<id>`, `image:<id>` and `audio:<id>`
     * document ids, which are the same row ids MediaStore exposes.
     */
    private fun mediaStoreUri(uri: Uri): Uri? = when (uri.authority) {
        MediaStore.AUTHORITY -> uri
        MEDIA_DOCUMENTS_AUTHORITY -> documentsMediaStoreUri(uri)
        else -> null
    }

    private fun documentsMediaStoreUri(uri: Uri): Uri? {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        val parts = documentId.split(':', limit = 2)
        if (parts.size != 2) return null
        val id = parts[1].toLongOrNull() ?: return null
        val collection = when (parts[0]) {
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> return null
        }
        return ContentUris.withAppendedId(collection, id)
    }

    private companion object {
        const val MEDIA_DOCUMENTS_AUTHORITY = "com.android.providers.media.documents"
    }
}
