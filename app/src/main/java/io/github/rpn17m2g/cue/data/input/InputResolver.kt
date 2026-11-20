package io.github.rpn17m2g.cue.data.input

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class InputResolutionException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The picked document cannot be copied into the cache. Byte counts are carried
 * so the screen can say how much is missing instead of blaming the file.
 */
class InsufficientStorageException(
    val neededBytes: Long,
    val availableBytes: Long,
) : InputResolutionException(
    "Not enough free space: $neededBytes bytes needed, $availableBytes available",
)

/**
 * A document is copied into the cache before FFmpeg can open it, and the
 * conversion writes its output next to that copy. A provider that does not
 * report a size cannot be checked, so the copy is attempted and its failure is
 * reported as before.
 */
internal fun copyFitsCache(documentBytes: Long?, usableBytes: Long): Boolean {
    if (documentBytes == null || documentBytes < 0) return true
    if (usableBytes < CACHE_HEADROOM_BYTES) return false
    return documentBytes <= usableBytes - CACHE_HEADROOM_BYTES
}

/** Room kept free beside the input copy for the MP3 that comes out of it. */
internal const val CACHE_HEADROOM_BYTES = 64L * 1_024 * 1_024

private data class DocumentInfo(val name: String, val sizeBytes: Long?)

/**
 * Copies a picked document into `cacheDir/input` so FFmpeg and Media3 can read a
 * plain file. For MP3 input the picker's write grant is persisted so a tag-only
 * edit can replace the original document.
 */
class InputResolver(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val cacheRoot: File
        get() = File(context.cacheDir, CACHE_DIRECTORY)

    suspend fun resolve(uri: Uri): ResolvedInput = withContext(ioDispatcher) {
        val document = queryDocument(uri)
        val mimeType = context.contentResolver.getType(uri)
        val id = UUID.randomUUID().toString()
        cacheRoot.mkdirs()
        // A long screen recording is often larger than what is left on the
        // device; failing before the copy turns into a clear sentence instead of
        // an out-of-space write error blamed on the file.
        val sizeBytes = document.sizeBytes
        val usableBytes = cacheRoot.usableSpace
        if (!copyFitsCache(sizeBytes, usableBytes)) {
            throw InsufficientStorageException(
                neededBytes = (sizeBytes ?: 0L) + CACHE_HEADROOM_BYTES,
                availableBytes = usableBytes,
            )
        }
        val target = File(cacheRoot, "$id-${safeFileName(document.name)}")
        val sniff = try {
            copyTo(uri, target)
        } catch (e: Exception) {
            target.delete()
            throw InputResolutionException("Could not read ${uri.lastPathSegment ?: "input"}", e)
        }
        val kind = InputKindDetector.detect(mimeType, document.name, sniff)
        ResolvedInput(
            id = id,
            uri = uri.toString(),
            path = target.absolutePath,
            displayName = document.name,
            mimeType = mimeType,
            kind = kind,
            durationMs = readDuration(File(target.absolutePath)),
            canEditInPlace = kind == InputKind.MP3 && requestWriteAccess(uri),
        )
    }

    fun cleanup(input: ResolvedInput) {
        File(input.path).delete()
    }

    fun cleanupAll() {
        cacheRoot.listFiles()?.forEach { it.delete() }
    }

    /**
     * Drops grants Cue no longer needs. Called when the session is cleared so
     * persisted picker grants do not accumulate.
     */
    fun releasePersistedPermissions() {
        val resolver = context.contentResolver
        resolver.persistedUriPermissions.toList().forEach { permission ->
            val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            if (flags != 0) {
                runCatching { resolver.releasePersistableUriPermission(permission.uri, flags) }
            }
        }
    }

    /**
     * SAF pickers grant transient read access plus, for editable documents, a
     * persistable read/write grant. MediaStore rows created by Cue are writable
     * without a persisted grant, but a shared media URI may carry only a read
     * grant, so write access is probed instead of inferred from the authority.
     */
    private fun requestWriteAccess(uri: Uri): Boolean {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return true
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        val resolver = context.contentResolver
        val readWrite = runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (readWrite.isSuccess) return true
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return canWrite(resolver, uri)
    }

    /**
     * Opens a non-truncating read/write descriptor. Providers reject the open
     * when the caller only holds a read grant, which is the only reliable check
     * for MediaStore rows owned by another app.
     */
    private fun canWrite(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, WRITE_PROBE)?.use { true } ?: false
    }.getOrDefault(false)

    private fun copyTo(uri: Uri, target: File): ByteArray {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw InputResolutionException("No readable stream for $uri")
        BufferedInputStream(stream).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1_024)
                val sniff = ByteArray(SNIFF_BYTES)
                var sniffed = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (sniffed < sniff.size) {
                        val take = minOf(read, sniff.size - sniffed)
                        buffer.copyInto(sniff, sniffed, 0, take)
                        sniffed += take
                    }
                    output.write(buffer, 0, read)
                }
                return sniff.copyOf(sniffed)
            }
        }
    }

    private fun queryDocument(uri: Uri): DocumentInfo {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val fromProvider = runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex < 0 || cursor.isNull(nameIndex)) {
                    null
                } else {
                    cursor.getString(nameIndex)
                }
                val size = if (sizeIndex < 0 || cursor.isNull(sizeIndex)) {
                    null
                } else {
                    cursor.getLong(sizeIndex).takeIf { it > 0 }
                }
                name to size
            }
        }.getOrNull()
        val name = fromProvider?.first?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: FALLBACK_NAME
        return DocumentInfo(name, fromProvider?.second)
    }

    private fun readDuration(file: File): Long? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
        }
    }.getOrNull()

    private fun safeFileName(displayName: String): String {
        val extension = displayName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) }
        val base = displayName.substringBeforeLast('.', displayName)
            .replace(ILLEGAL, " ")
            .trim()
            .take(MAX_SAFE_NAME)
            .ifBlank { FALLBACK_NAME }
        return if (extension == null) base else "$base.$extension"
    }

    private companion object {
        const val CACHE_DIRECTORY = "input"
        const val SNIFF_BYTES = 12
        const val MAX_SAFE_NAME = 80
        const val FALLBACK_NAME = "input"
        const val WRITE_PROBE = "rw"
        val ILLEGAL = Regex("[<>:\"/\\\\|?*\\p{Cntrl}\\s]+")
    }
}
