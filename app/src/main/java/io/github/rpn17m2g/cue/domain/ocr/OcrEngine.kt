package io.github.rpn17m2g.cue.domain.ocr

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import java.io.File

/** One still frame written to the cache so OCR engines can read it as a file. */
data class OcrFrame(
    val timestampMs: Long,
    val file: File,
)

/** The lines an engine read from one frame, in reading order. */
data class OcrFrameText(
    val timestampMs: Long,
    val lines: List<String>,
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
