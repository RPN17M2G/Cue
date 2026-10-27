package io.github.rpn17m2g.cue.data.input

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Replaces a SAF/MediaStore document in place. Content providers cannot offer an
 * atomic replace, so callers keep the untouched cache copy as a backup and
 * restore it when the write or its verification fails.
 *
 * URIs cross this boundary as strings so the caller stays free of Android types.
 */
interface DocumentEditor {

    /** Overwrites the document at [uri] with [source] and checks that it arrived. */
    suspend fun replace(uri: String, source: File)

    /** Copies the current document bytes into [target] so they can be verified. */
    suspend fun copyTo(uri: String, target: File)
}

class ContentResolverDocumentEditor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentEditor {

    override suspend fun replace(uri: String, source: File): Unit = withContext(ioDispatcher) {
        val document = Uri.parse(uri)
        val expectedBytes = source.length()
        val output = context.contentResolver.openOutputStream(document, WRITE_TRUNCATE)
            ?: throw IOException("The provider returned no write stream for $uri")
        output.use { stream ->
            source.inputStream().use { input -> input.copyTo(stream) }
            stream.flush()
            (stream as? FileOutputStream)?.fd?.sync()
        }
        val written = documentSize(document)
        if (written != null && expectedBytes >= 0 && written != expectedBytes) {
            throw IOException("Expected $expectedBytes bytes in $uri but the provider reported $written")
        }
    }

    override suspend fun copyTo(uri: String, target: File): Unit = withContext(ioDispatcher) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw IOException("The provider returned no read stream for $uri")
        input.use { stream ->
            target.outputStream().use { output -> stream.copyTo(output) }
        }
    }

    private fun documentSize(uri: Uri): Long? =
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, READ_ONLY)?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 }

    private companion object {
        const val WRITE_TRUNCATE = "rwt"
        const val READ_ONLY = "r"
    }
}
