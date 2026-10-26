package io.github.rpn17m2g.cue.domain.ocr

import io.github.rpn17m2g.cue.settings.AppSettings

/**
 * Chooses the timestamps sampled for OCR. The scan window only bounds the start
 * of the recording: a recording shorter than the window is sampled to its end,
 * and a recording longer than the window is never sampled past it.
 */
object FrameSampler {

    const val MAX_FRAMES = 120

    fun timestamps(
        durationMs: Long?,
        scanWindowSeconds: Int,
        intervalMs: Long,
    ): List<Long> {
        val windowSeconds = scanWindowSeconds.coerceIn(
            AppSettings.SCAN_WINDOW_RANGE.first,
            AppSettings.SCAN_WINDOW_RANGE.last,
        )
        val windowMs = windowSeconds * 1_000L
        val interval = intervalMs.takeIf { it >= MIN_INTERVAL_MS }
            ?: AppSettings.DEFAULT_OCR_INTERVAL_MS
        val endMs = durationMs?.takeIf { it > 0L }?.coerceAtMost(windowMs) ?: windowMs
        if (endMs <= 0L) return listOf(0L)

        val timestamps = ArrayList<Long>()
        var timestamp = 0L
        while (timestamp < endMs && timestamps.size < MAX_FRAMES) {
            timestamps += timestamp
            timestamp += interval
        }
        return timestamps.ifEmpty { listOf(0L) }
    }

    private const val MIN_INTERVAL_MS = 250L
}
