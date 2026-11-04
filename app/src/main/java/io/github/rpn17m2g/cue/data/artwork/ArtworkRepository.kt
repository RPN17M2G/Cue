package io.github.rpn17m2g.cue.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import io.github.rpn17m2g.cue.data.metadata.ArtworkCandidate
import io.github.rpn17m2g.cue.data.metadata.MetadataClients
import io.github.rpn17m2g.cue.domain.model.Artwork
import io.github.rpn17m2g.cue.tags.ArtworkEncoder
import io.github.rpn17m2g.cue.tags.ArtworkScaling
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cover search, download, and local-image import for the artwork picker. Downloaded
 * bytes are re-encoded through [ArtworkEncoder] so every stored cover is a
 * reasonable-size JPEG with an honest MIME type.
 */
class ArtworkRepository(
    private val context: Context,
    private val clients: suspend () -> MetadataClients,
    private val encoder: ArtworkEncoder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val previews = object : LruCache<String, Bitmap>(PREVIEW_CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun search(term: String): List<ArtworkCandidate> {
        if (term.isBlank()) return emptyList()
        return try {
            clients().artworkSearch.search(term.trim())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun load(candidate: ArtworkCandidate): Artwork? =
        fetch(candidate.artworkUrl600)?.let { withContext(ioDispatcher) { encode(it, EMBED_MAX_DIMENSION) } }

    /** Downloads a provider-supplied cover URL, e.g. the best match's artwork. */
    suspend fun fromUrl(url: String): Artwork? =
        fetch(url)?.let { withContext(ioDispatcher) { encode(it, EMBED_MAX_DIMENSION) } }

    suspend fun fromUri(uri: Uri): Artwork? = withContext(ioDispatcher) {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readCapped(it, MAX_LOCAL_BYTES) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        encode(bytes, EMBED_MAX_DIMENSION)
    }

    /** A small cached bitmap for the cover grid. */
    suspend fun preview(url: String): Bitmap? {
        previews.get(url)?.let { return it }
        val bytes = fetch(url) ?: return null
        val bitmap = withContext(ioDispatcher) { decode(bytes, PREVIEW_MAX_DIMENSION) } ?: return null
        previews.put(url, bitmap)
        return bitmap
    }

    private suspend fun fetch(url: String): ByteArray? = try {
        clients().artworkFetcher.fetch(url)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun encode(bytes: ByteArray, maxDimension: Int): Artwork? = runCatching {
        val encoded = encoder.downscaleToJpeg(bytes, maxDimension)
        Artwork(encoded.bytes, encoded.mimeType)
    }.getOrNull()

    private fun decode(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = ArtworkScaling.sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val target = ArtworkScaling.fitInside(decoded.width, decoded.height, maxDimension)
        if (target.width == decoded.width && target.height == decoded.height) return decoded
        return Bitmap.createScaledBitmap(decoded, target.width, target.height, true).also {
            decoded.recycle()
        }
    }

    private fun readCapped(input: InputStream, limit: Int): ByteArray? = input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return@use null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    private companion object {
        const val EMBED_MAX_DIMENSION = 1_000
        const val PREVIEW_MAX_DIMENSION = 320
        const val PREVIEW_CACHE_KB = 16 * 1024
        const val MAX_LOCAL_BYTES = 24 * 1024 * 1024
        const val BUFFER_SIZE = 16 * 1024
    }
}
