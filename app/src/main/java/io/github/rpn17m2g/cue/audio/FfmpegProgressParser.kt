package io.github.rpn17m2g.cue.audio

/**
 * Incremental parser for FFmpeg stderr. Progress is monotonic, but failure is
 * authoritative from the process exit code: FFmpeg can print error-looking lines
 * during a successful conversion, so [State.fatalError] only latches a small set
 * of unambiguous messages while [State.lastErrorLines] keeps diagnostics.
 */
class FfmpegProgressParser(
    private val totalDurationMs: Long? = null,
) {

    data class State(
        val positionMs: Long? = null,
        val progress: Float = 0f,
        val isComplete: Boolean = false,
        val fatalError: String? = null,
        val lastErrorLines: List<String> = emptyList(),
    )

    var state: State = State()
        private set

    fun update(chunk: String): State {
        chunk.split('\r', '\n').forEach { raw ->
            val line = raw.trim()
            if (line.isNotEmpty()) consume(line)
        }
        return state
    }

    fun onLine(line: String): State = update(line)

    fun progress(totalDurationMs: Long?): Float = fraction(state.positionMs, totalDurationMs)

    private fun consume(line: String) {
        val message = stripLogPrefix(line)

        if (isDiagnostic(message)) {
            state = state.copy(lastErrorLines = (state.lastErrorLines + message).takeLast(MAX_DIAGNOSTICS))
        }
        if (state.fatalError == null && isFatal(message)) {
            state = state.copy(fatalError = message)
        }

        val reported = parsePosition(message)
        if (reported != null) {
            val position = maxOf(state.positionMs ?: reported, reported).coerceAtLeast(0)
            state = state.copy(positionMs = position, progress = fraction(position, totalDurationMs))
        }

        if (endPattern.containsMatchIn(message)) {
            val completedPosition = if (totalDurationMs != null && totalDurationMs > 0) {
                maxOf(state.positionMs ?: 0, totalDurationMs)
            } else {
                state.positionMs
            }
            state = state.copy(
                positionMs = completedPosition,
                progress = fraction(completedPosition, totalDurationMs),
                isComplete = true,
            )
        }
    }

    private fun parsePosition(line: String): Long? {
        timePattern.find(line)?.let { match ->
            val clock = match.groupValues[1]
            if (!clock.startsWith("-")) {
                FfmpegTime.parseClock(clock)?.let { return it }
            }
        }
        outTimeMsPattern.find(line)?.let { match ->
            match.groupValues[1].toLongOrNull()?.let { return nonNegativeMicrosToMillis(it) }
        }
        outTimeUsPattern.find(line)?.let { match ->
            match.groupValues[1].toLongOrNull()?.let { return nonNegativeMicrosToMillis(it) }
        }
        return null
    }

    private fun nonNegativeMicrosToMillis(microseconds: Long): Long? =
        if (microseconds < 0) null else microseconds / 1_000

    private fun stripLogPrefix(line: String): String = line.replaceFirst(logPrefixPattern, "")

    private fun isDiagnostic(line: String): Boolean = diagnosticPrefixes.any { line.startsWith(it) }

    private fun isFatal(line: String): Boolean = fatalPrefixes.any { line.startsWith(it) }

    private fun fraction(positionMs: Long?, durationMs: Long?): Float {
        if (positionMs == null || durationMs == null || durationMs <= 0) return 0f
        return (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val MAX_DIAGNOSTICS = 20

        val logPrefixPattern = Regex("""^\[[^\]]*]\s*""")
        val timePattern = Regex("""(?:^|[\s;])time=(-?\d+:\d{1,2}:\d{1,2}(?:\.\d+)?)""")
        val outTimeMsPattern = Regex("""(?:^|[\s;])out_time_ms=(-?\d+)""")
        val outTimeUsPattern = Regex("""(?:^|[\s;])out_time_us=(-?\d+)""")
        val endPattern = Regex("""(?:^|[\s;])progress=end(?:$|[\s;])""")

        val diagnosticPrefixes = listOf("Error", "Invalid data", "No such file", "Could not", "Conversion failed")
        val fatalPrefixes = listOf(
            "Conversion failed!",
            "Error opening input",
            "Invalid data found when processing input",
            "No such file or directory",
        )
    }
}
