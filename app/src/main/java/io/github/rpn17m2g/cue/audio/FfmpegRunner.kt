package io.github.rpn17m2g.cue.audio

fun interface FfmpegRunner {
    suspend fun run(args: List<String>, onLine: (String) -> Unit): Int
}
