package io.github.rpn17m2g.cue.domain.ocr

/**
 * One ranked reading of a song's on-screen details.
 *
 * [artist] is null when the frames only revealed a title.
 */
data class OcrCandidate(
    val title: String,
    val artist: String? = null,
    val score: Double = 0.0,
)
