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
            timestampsMs.mapNotNull { timestamp ->
                readFrame(retriever, timestamp, outputDir)
            }
        } finally {
            retriever.release()
        }
    }

    private fun readFrame(
        retriever: MediaMetadataRetriever,
        timestampMs: Long,
        outputDir: File,
    ): OcrFrame? {
        val frame = frameAt(retriever, timestampMs) ?: return null
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

    private fun frameAt(retriever: MediaMetadataRetriever, timestampMs: Long): Bitmap? {
        val timeUs = timestampMs * 1_000L
        return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_LONG_EDGE) return bitmap
        val ratio = MAX_LONG_EDGE.toFloat() / longEdge
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        const val MAX_LONG_EDGE = 1_280
        const val JPEG_QUALITY = 85
    }
}
