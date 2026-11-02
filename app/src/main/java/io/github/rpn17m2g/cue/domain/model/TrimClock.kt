package io.github.rpn17m2g.cue.domain.model

/**
 * Turns trim handles into the `m:ss` text the form shows and back again.
 *
 * Kept in the domain, not the screen, because a typed trim is a promise about
 * what will be cut: the parser has to reject `1:75` rather than silently treat
 * it as `2:15`, and the formatter has to round-trip through the parser.
 */
object TrimClock {

    /** `m:ss`, or `h:mm:ss` once the clip is longer than an hour. */
    fun format(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "$hours:${minutes.padded()}:${seconds.padded()}"
        } else {
            "$minutes:${seconds.padded()}"
        }
    }

    /**
     * Accepts `ss`, `m:ss`, `mm:ss` and `h:mm:ss`. Returns null for anything
     * else, so the caller keeps the last good value instead of moving a handle
     * to zero while the user is still typing.
     */
    fun parse(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(':')
        if (parts.size > 3) return null
        val numbers = parts.map { part ->
            part.toLongOrNull()?.takeIf { it >= 0 } ?: return null
        }
        // Only the leading unit may exceed 59: "75:00" is 75 minutes, "1:75"
        // is a typo. Requiring two digits after a colon also catches "1:5".
        if (numbers.drop(1).any { it > 59 }) return null
        if (parts.drop(1).any { it.length != 2 }) return null
        val seconds = when (numbers.size) {
            1 -> numbers[0]
            2 -> numbers[0] * 60 + numbers[1]
            else -> numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
        }
        return seconds * 1_000
    }

    private fun Long.padded(): String = toString().padStart(2, '0')
}
