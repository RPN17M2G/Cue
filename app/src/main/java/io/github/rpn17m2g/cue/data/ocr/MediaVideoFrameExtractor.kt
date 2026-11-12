package io.github.rpn17m2g.cue.data.ocr

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.github.rpn17m2g.cue.domain.ocr.FrameExtractor
import io.github.rpn17m2g.cue.domain.ocr.OcrFrame
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Samples frames with MediaMetadataRetriever and stores them as JPEGs so OCR
 * engines can read them without holding several full-size bitmaps in memory.
 *
 * On Android 12+ the frame is decoded directly at the size OCR needs. Decoding
 * a 4K frame and then shrinking it allocates the full bitmap first, which is
 * both slower and the main source of memory pressure on a long scan.
 */
class MediaVideoFrameExtractor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FrameExtractor {

    override suspend fun extract(
        video: File,
        timestampsMs: List<Long>,
        outputDir: File,
    ): List<OcrFrame> = withContext(ioDispatcher) {
        outputDir.mkdirs()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val target = retriever.targetSize()
            timestampsMs.mapNotNull { timestamp ->
                readFrame(retriever, timestamp, outputDir, target)
            }
        } finally {
            retriever.release()
        }
    }

    private fun readFrame(
        retriever: MediaMetadataRetriever,
        timestampMs: Long,
        outputDir: File,
        target: FrameSize?,
    ): OcrFrame? {
        val frame = frameAt(retriever, timestampMs, target) ?: return null
        val scaled = scaleDown(frame)
        return try {
            val file = File(outputDir, "frame-$timestampMs.jpg")
            val written = FileOutputStream(file).use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            if (written) OcrFrame(timestampMs, file) else {
                file.delete()
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
        }
    }

    private fun frameAt(
        retriever: MediaMetadataRetriever,
        timestampMs: Long,
        target: FrameSize?,
    ): Bitmap? {
        val timeUs = timestampMs * 1_000L
        if (target != null) {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                target.width,
                target.height,
            )?.let { return it }
        }
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun MediaMetadataRetriever.targetSize(): FrameSize? {
        val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?: return null
        val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?: return null
        val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull()
            ?: 0
        return frameTargetSize(width, height, rotation)
    }

    /** Safety net for the fallback path and for an unexpected aspect mismatch. */
    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val target = frameTargetSize(bitmap.width, bitmap.height, rotationDegrees = 0)
            ?: return bitmap
        if (target.width >= bitmap.width && target.height >= bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
    }

    private companion object {
        const val MAX_LONG_EDGE = 1_280
        const val JPEG_QUALITY = 85
    }
}

internal data class FrameSize(val width: Int, val height: Int)

/**
 * Long-edge limit applied to OCR frames. [MediaMetadataRetriever] returns the
 * frame in its display orientation, so a 90/270° rotation swaps the metadata
 * width and height before the scale is computed.
 */
internal fun frameTargetSize(
    width: Int,
    height: Int,
    rotationDegrees: Int,
    maxLongEdge: Int = 1_280,
): FrameSize? {
    if (width <= 0 || height <= 0 || maxLongEdge <= 0) return null
    val rotated = rotationDegrees % 180 != 0
    val displayWidth = if (rotated) height else width
    val displayHeight = if (rotated) width else height
    val longEdge = maxOf(displayWidth, displayHeight)
    if (longEdge <= maxLongEdge) return FrameSize(displayWidth, displayHeight)
    val ratio = maxLongEdge.toFloat() / longEdge
    return FrameSize(
        width = (displayWidth * ratio).toInt().coerceAtLeast(1),
        height = (displayHeight * ratio).toInt().coerceAtLeast(1),
    )
}
