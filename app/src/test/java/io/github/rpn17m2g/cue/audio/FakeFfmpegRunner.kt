package io.github.rpn17m2g.cue.audio

class FakeFfmpegRunner(
    private val exitCode: Int = 0,
    private val lines: List<String> = emptyList(),
    private val failure: Throwable? = null,
) : FfmpegRunner {

    val invocations = mutableListOf<List<String>>()

    override suspend fun run(args: List<String>, onLine: (String) -> Unit): Int {
        invocations += args
        failure?.let { throw it }
        lines.forEach(onLine)
        return exitCode
    }
}
