package io.github.rpn17m2g.cue.domain.identify

/**
 * Wall-clock cost of the identification stages, in milliseconds. The app logs
 * these in debug builds so a slow recording can be traced to the stage that
 * spent the time instead of guessed at.
 *
 * The two OCR passes run in parallel, so [latinOcrMs] and [hebrewOcrMs] can add
 * up to more than [ocrMs]; [ocrMs] is the wall time of the parallel section.
 */
data class StageTimings(
    val framesMs: Long = 0,
    val latinOcrMs: Long = 0,
    val hebrewOcrMs: Long = 0,
    val ocrMs: Long = 0,
    val cascadeMs: Long = 0,
    val enrichmentMs: Long = 0,
) {
    /** Frame extraction plus OCR: the part that runs before any network call. */
    val localMs: Long get() = framesMs + ocrMs

    val totalMs: Long get() = framesMs + ocrMs + cascadeMs + enrichmentMs
}
