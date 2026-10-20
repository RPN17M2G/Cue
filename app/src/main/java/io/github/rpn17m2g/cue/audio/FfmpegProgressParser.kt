package io.github.rpn17m2g.cue.audio

class FfmpegProgressParser(
    private val totalDurationMs: Long? = null,
) {

    data class State(
        val positionMs: Long? = null,
        val progress: Float = 0f,
        val isComplete: Boolean = false,
        val fatalError: String? = null,
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
        if (state.fatalError == null && isFatal(line)) {
            state = state.copy(fatalError = line)
        }

        val reported = parsePosition(line)
        if (reported != null) {
            val position = maxOf(state.positionMs ?: reported, reported).coerceAtLeast(0)
            state = state.copy(positionMs = position, progress = fraction(position, totalDurationMs))
        }

        if (endPattern.containsMatchIn(line)) {
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
            FfmpegTime.parseClock(match.groupValues[1])?.let { return it }
        }
        outTimeMsPattern.find(line)?.let { match ->
            match.groupValues[1].toLongOrNull()?.let { return it }
        }
        outTimeUsPattern.find(line)?.let { match ->
            match.groupValues[1].toLongOrNull()?.let { return it / 1_000 }
        }
        return null
    }

    private fun isFatal(line: String): Boolean = fatalPrefixes.any { line.startsWith(it) }

    private fun fraction(positionMs: Long?, durationMs: Long?): Float {
        if (positionMs == null || durationMs == null || durationMs <= 0) return 0f
        return (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        val timePattern = Regex("""(?:^|[\s;])time=(\d+:\d{1,2}:\d{1,2}(?:\.\d+)?)""")
        val outTimeMsPattern = Regex("""(?:^|[\s;])out_time_ms=(-?\d+)""")
        val outTimeUsPattern = Regex("""(?:^|[\s;])out_time_us=(-?\d+)""")
        val endPattern = Regex("""(?:^|[\s;])progress=end(?:$|[\s;])""")
        val fatalPrefixes = listOf("Error", "Invalid data", "No such file", "Could not", "Conversion failed")
    }
}
