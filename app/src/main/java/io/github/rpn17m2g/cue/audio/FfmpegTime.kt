package io.github.rpn17m2g.cue.audio

internal object FfmpegTime {

    private val clockPattern = Regex("""(\d+):(\d{1,2}):(\d{1,2})(?:\.(\d+))?""")

    fun parseClock(text: String): Long? {
        val match = clockPattern.find(text) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        if (minutes > 59 || seconds > 59) return null
        val millis = fractionToMillis(match.groupValues[4])
        return ((hours * 60 + minutes) * 60 + seconds) * 1_000 + millis
    }

    fun formatSeconds(milliseconds: Long): String {
        require(milliseconds >= 0) { "milliseconds must be non-negative" }
        val seconds = milliseconds / 1_000
        val remainder = milliseconds % 1_000
        return "$seconds.${remainder.toString().padStart(3, '0')}"
    }

    private fun fractionToMillis(fraction: String): Long {
        if (fraction.isEmpty()) return 0
        return fraction.padEnd(3, '0').take(3).toLongOrNull() ?: 0
    }
}
