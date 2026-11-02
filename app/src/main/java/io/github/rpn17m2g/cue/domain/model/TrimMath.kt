package io.github.rpn17m2g.cue.domain.model

/** Clamping rules shared by the trim slider and the processing request. */
object TrimMath {

    const val MIN_DURATION_MS = 1_000L

    /**
     * Keeps both handles inside the track and at least [MIN_DURATION_MS] apart,
     * moving the handle that was not being dragged.
     */
    fun clampWindow(startMs: Long, endMs: Long, durationMs: Long): LongRange {
        require(durationMs > 0) { "durationMs must be positive" }
        val minSpan = minOf(MIN_DURATION_MS, durationMs)
        var start = startMs.coerceIn(0L, durationMs - minSpan)
        var end = endMs.coerceIn(start + minSpan, durationMs)
        if (end - start < minSpan) {
            if (startMs != start) {
                start = (end - minSpan).coerceAtLeast(0)
            } else {
                end = (start + minSpan).coerceAtMost(durationMs)
            }
        }
        return start..end
    }

    /**
     * True when [startMs] can become the start without [clampWindow] moving it:
     * inside the track and at least the minimum span before [endMs]. Typed
     * values are checked with this so the field never promises a window the
     * slider then silently changes.
     */
    fun acceptsStart(startMs: Long, endMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return startMs >= 0 && startMs <= durationMs &&
            endMs - startMs >= minOf(MIN_DURATION_MS, durationMs)
    }

    /** True when [endMs] can become the end without [clampWindow] moving it. */
    fun acceptsEnd(endMs: Long, startMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0) return false
        return endMs >= 0 && endMs <= durationMs &&
            endMs - startMs >= minOf(MIN_DURATION_MS, durationMs)
    }

    /** `null` means "use the whole input" and lets FFmpeg skip the trim arguments. */
    fun trimRange(startMs: Long, endMs: Long, durationMs: Long?): TrimRange? {
        if (durationMs == null || durationMs <= 0) return null
        val start = startMs.coerceIn(0L, durationMs)
        val end = endMs.coerceIn(0L, durationMs)
        if (end - start < MIN_DURATION_MS) return null
        if (start == 0L && end == durationMs) return null
        return TrimRange(start, end)
    }

    fun effectiveDurationMs(trim: TrimRange?, durationMs: Long?): Long? =
        trim?.durationMs ?: durationMs?.takeIf { it > 0 }
}
