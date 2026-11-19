package io.github.rpn17m2g.cue.domain.model

import java.util.Locale

/** One lyric line and the point in the audio where it starts, in milliseconds. */
data class LyricLine(
    val startMs: Long,
    val text: String,
)

/**
 * Parses LRC into timed lines.
 *
 * Accepts `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]` and the colon-fraction variant
 * (`[mm:ss:xx]`), plus several timestamps on one line (a repeated chorus). LRC
 * metadata tags such as `[ar:...]`, untimed text and blank lines are ignored.
 * The result is sorted by time, so a file whose lines are out of order still
 * reads in playing order.
 */
fun parseLrc(lrc: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    lrc.lineSequence().forEach { raw ->
        val starts = mutableListOf<Long>()
        var index = 0
        while (index < raw.length) {
            val stamp = LRC_TIMESTAMP.find(raw, index) ?: break
            if (stamp.range.first != index) break
            starts += stamp.toMillis()
            index = stamp.range.last + 1
        }
        if (starts.isEmpty()) return@forEach
        val text = raw.substring(index).trim()
        if (text.isEmpty()) return@forEach
        starts.forEach { lines += LyricLine(it, text) }
    }
    return lines.sortedBy { it.startMs }
}

/**
 * Canonical LRC for [this], with centisecond precision. Reading a SYLT frame
 * back produces the same text as the LRC it was written from, which is what the
 * post-write verification compares.
 */
fun List<LyricLine>.toLrc(): String = joinToString("\n") { line ->
    val centis = (line.startMs.coerceAtLeast(0) + 5) / 10
    String.format(
        Locale.ROOT,
        "[%02d:%02d.%02d]%s",
        centis / 6_000,
        (centis / 100) % 60,
        centis % 100,
        line.text,
    )
}

/**
 * Re-times the list for a clip that starts at [clipStartMs] in the original.
 * Lines before the clip are dropped, except the last one, which clamps to zero
 * so the first thing heard still has its words on screen. That clamped line is
 * dropped when a line starts exactly at the cut: both would claim zero and a
 * player can only show one. [clipEndMs] drops lines that fall after the clip.
 */
fun List<LyricLine>.shiftedTo(
    clipStartMs: Long,
    clipEndMs: Long? = null,
): List<LyricLine> {
    if (clipStartMs <= 0L && clipEndMs == null) return this
    val inside = filter { clipEndMs == null || it.startMs < clipEndMs }
    val firstInside = inside.firstOrNull { it.startMs >= clipStartMs }
    val leading = inside
        .lastOrNull { it.startMs < clipStartMs }
        ?.takeIf { firstInside == null || firstInside.startMs > clipStartMs }
    val rest = inside
        .filter { it.startMs >= clipStartMs }
        .map { it.copy(startMs = it.startMs - clipStartMs) }
    return listOfNotNull(leading?.copy(startMs = 0L)) + rest
}

private fun MatchResult.toMillis(): Long {
    val fraction = groupValues[3]
    val millis = when {
        fraction.isEmpty() -> 0L
        fraction.length == 1 -> fraction.toLong() * 100
        fraction.length == 2 -> fraction.toLong() * 10
        else -> fraction.take(3).toLong()
    }
    return groupValues[1].toLong() * 60_000 + groupValues[2].toLong() * 1_000 + millis
}

private val LRC_TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
