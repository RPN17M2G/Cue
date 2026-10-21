package io.github.rpn17m2g.cue.audio

/**
 * The service-level result of one FFmpeg invocation. Only the process exit code
 * decides [failed]; stderr diagnostics are kept for reporting and never override
 * a successful exit.
 */
data class FfmpegOutcome(
    val exitCode: Int,
    val progress: Float,
    val diagnostics: List<String>,
    val failed: Boolean,
)

fun FfmpegProgressParser.State.toOutcome(exitCode: Int): FfmpegOutcome = FfmpegOutcome(
    exitCode = exitCode,
    progress = progress,
    diagnostics = lastErrorLines,
    failed = exitCode != 0,
)
