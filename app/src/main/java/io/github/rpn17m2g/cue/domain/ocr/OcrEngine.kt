package io.github.rpn17m2g.cue.domain.ocr

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import java.io.File

/** One still frame written to the cache so OCR engines can read it as a file. */
data class OcrFrame(
    val timestampMs: Long,
    val file: File,
)

/**
 * Where a line sits in its frame, as fractions of the frame size so the
 * aggregator does not care about the recording's resolution.
 */
data class OcrBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f

    /** Shared width as a fraction of the narrower line, `0f` when they miss. */
    fun horizontalOverlap(other: OcrBox): Float {
        val shared = (minOf(right, other.right) - maxOf(left, other.left)).coerceAtLeast(0f)
        val narrower = minOf(width, other.width)
        return if (narrower <= 0f) 0f else (shared / narrower).coerceIn(0f, 1f)
    }
}

/** One line an engine read, with its position when the engine reports one. */
data class OcrLine(
    val text: String,
    val box: OcrBox? = null,
    val confidence: Float? = null,
)

/** The lines an engine read from one frame. */
data class OcrFrameText(
    val timestampMs: Long,
    val lines: List<OcrLine>,
)

/**
 * Reads text from sampled video frames. Implementations own their engine and
 * return one entry per input frame so the aggregator can count frame support.
 */
interface OcrEngine {
    val source: IdentificationSource

    suspend fun recognize(frames: List<OcrFrame>): List<OcrFrameText>
}

/** Writes the planned video timestamps to cache as images. */
interface FrameExtractor {
    suspend fun extract(
        video: File,
        timestampsMs: List<Long>,
        outputDir: File,
    ): List<OcrFrame>
}
