package io.github.rpn17m2g.cue.audio

/**
 * Runs FFmpeg and forwards its stderr line by line. The returned exit code is the
 * authoritative success signal; parsed stderr is diagnostics and progress only.
 */
fun interface FfmpegRunner {
    suspend fun run(args: List<String>, onLine: (String) -> Unit): Int
}
