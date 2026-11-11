package io.github.rpn17m2g.cue.domain.ocr

/**
 * Reads the length of the song from the "now playing" UI.
 *
 * AcoustID is a full-file service: it needs the duration of the whole track, not
 * the duration of the recorded excerpt. A player screen almost always shows both
 * the position and the total ("0:42 ... 3:24"), which is the only reliable way
 * to learn the track length from a screen recording. A 30 second clip of a 3:24
 * song has to be reported as 204 seconds, otherwise AcoustID filters the
 * recording out before it compares any audio.
 *
 * Lines in the status bar are ignored: the phone's own clock ("9:41") is a
 * perfectly valid m:ss token and would otherwise look like a 9 minute track.
 */
object TrackDuration {

    /** Fraction of the frame height below which text is phone chrome, not the player. */
    private const val STATUS_BAR_BOTTOM = 0.08f

    private const val MIN_SECONDS = 20L
    private const val MAX_SECONDS = 7_200L

    /**
     * A lone clock reading is ambiguous: it could be the position (usually under
     * a minute) or the total. Only treat it as the track length when it is long
     * enough to be a whole song.
     */
    private const val SINGLE_READING_MIN_SECONDS = 45L

    private val CLOCK = Regex("(?<![\\d:])(?:(\\d{1,2}):)?(\\d{1,3}):([0-5]\\d)(?![\\d:])")

    fun fromFrameTexts(texts: List<OcrFrameText>): Long? {
        val perFrame = texts.mapNotNull { frame -> frameReading(frame) }
        if (perFrame.isEmpty()) return null
        // The total is stable while the position ticks, so the most frequent
        // reading wins. Ties go to the longer one.
        val best = perFrame
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenByDescending { it.key })
            .first()
            .key
        return best * 1_000L
    }

    private fun frameReading(frame: OcrFrameText): Long? {
        val readings = frame.lines
            .filter { line -> line.box == null || line.box.top >= STATUS_BAR_BOTTOM }
            .flatMap { line -> clockValues(line.text) }
            .distinct()
        val chosen = when {
            readings.size >= 2 -> readings.maxOrNull()
            readings.size == 1 && readings[0] >= SINGLE_READING_MIN_SECONDS -> readings[0]
            else -> null
        }
        return chosen?.takeIf { it in MIN_SECONDS..MAX_SECONDS }
    }

    /** Every plausible clock value in one line of text, in seconds. */
    fun clockValues(text: String): List<Long> =
        CLOCK.findAll(text).mapNotNull { match ->
            val hours = match.groupValues[1].toLongOrNull() ?: 0L
            val minutes = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val seconds = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
            hours * 3_600L + minutes * 60L + seconds
        }.filter { it in MIN_SECONDS..MAX_SECONDS }.toList()
}
