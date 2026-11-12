package io.github.rpn17m2g.cue.domain.ocr

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OcrRecognition(
    val candidates: List<OcrCandidate>,
    val rawLines: List<String>,
    val hebrewUsed: Boolean,
    val failed: Boolean,
    /** Whole-track length read from the player UI, when one was visible. */
    val trackDurationMs: Long? = null,
    /** Wall time each pass took, and the wall time of the parallel section. */
    val latinOcrMs: Long = 0,
    val hebrewOcrMs: Long = 0,
    val ocrMs: Long = 0,
)

/**
 * Runs the Latin engine, then the Hebrew one, and keeps the better reading.
 *
 * The passes are always both run. A shortcut that trusted a strong Latin score
 * was wrong in practice: ML Kit reads a Hebrew drawing of a date in the status
 * bar as Latin text ("יום ד׳ 28" became "vIN 28"), which is a confident but
 * meaningless line. Both readings are cheap next to frame extraction and the
 * online lookup, so the scores decide.
 */
class DualOcrRecognizer(
    private val latin: OcrEngine,
    private val hebrew: OcrEngine,
) {

    /**
     * ML Kit's recognizer is shared and the review screen can start a second
     * identification before the first one is done (the generation guard drops
     * the stale result, not the work). One OCR batch at a time keeps the shared
     * engine out of two callers' hands; the two scripts still run in parallel
     * inside a batch.
     */
    private val batchLock = Mutex()

    suspend fun recognize(
        frames: List<OcrFrame>,
        preferHebrew: Boolean,
    ): OcrRecognition {
        if (frames.isEmpty()) {
            return OcrRecognition(emptyList(), emptyList(), hebrewUsed = false, failed = true)
        }

        return batchLock.withLock {
            coroutineScope {
                val sectionStarted = System.nanoTime()
                // The two engines read the same frames independently, so there
                // is no reason to pay for one after the other. Both always run;
                // only the wall time changes.
                val latinJob = async { timed { readSafely(latin, frames) } }
                val hebrewJob = async { timed { readSafely(hebrew, frames) } }
                val (latinText, latinMs) = latinJob.await()
                val (hebrewText, hebrewMs) = hebrewJob.await()
                val ocrMs = elapsedMs(sectionStarted)

                val latinCandidates = candidates(frames, latinText)
                val latinScore = strongScore(latinCandidates)

                val hebrewCandidates = candidates(frames, hebrewText)
                val hebrewScore = strongScore(hebrewCandidates)
                val useHebrew = chooseHebrew(preferHebrew, latinScore, hebrewScore)

                if (useHebrew) {
                    OcrRecognition(
                        candidates = hebrewCandidates,
                        rawLines = rawLines(hebrewText, latinText),
                        hebrewUsed = true,
                        failed = latinText == null && hebrewText == null,
                        trackDurationMs = TrackDuration.fromFrameTexts(
                            winningTexts(hebrewText, latinText),
                        ),
                        latinOcrMs = latinMs,
                        hebrewOcrMs = hebrewMs,
                        ocrMs = ocrMs,
                    )
                } else {
                    OcrRecognition(
                        candidates = latinCandidates,
                        rawLines = rawLines(latinText, hebrewText),
                        hebrewUsed = false,
                        failed = latinText == null && hebrewText == null,
                        trackDurationMs = TrackDuration.fromFrameTexts(
                            winningTexts(latinText, hebrewText),
                        ),
                        latinOcrMs = latinMs,
                        hebrewOcrMs = hebrewMs,
                        ocrMs = ocrMs,
                    )
                }
            }
        }
    }

    private suspend fun <T> timed(block: suspend () -> T): Pair<T, Long> {
        val started = System.nanoTime()
        val value = block()
        return value to elapsedMs(started)
    }

    private fun elapsedMs(startedNanos: Long): Long =
        (System.nanoTime() - startedNanos) / 1_000_000L

    /**
     * The preferred script wins a near tie, but a clearly better reading from
     * the other engine always wins. Tesseract reads Latin text as gibberish
     * Hebrew, so a Hebrew pass that scores much lower than the Latin one is not
     * allowed to replace it just because the app is in Hebrew.
     */
    private fun chooseHebrew(
        preferHebrew: Boolean,
        latinScore: Double,
        hebrewScore: Double,
    ): Boolean = when {
        hebrewScore < 0.0 && latinScore < 0.0 -> preferHebrew
        hebrewScore < 0.0 -> false
        latinScore < 0.0 -> true
        preferHebrew -> hebrewScore >= latinScore * HEBREW_MARGIN
        else -> hebrewScore > latinScore * LATIN_MARGIN
    }

    /** The best plausible score, or a negative value when nothing is plausible. */
    private fun strongScore(candidates: List<OcrCandidate>): Double =
        candidates.firstOrNull()?.takeIf(::isPlausible)?.score ?: -1.0

    private suspend fun readSafely(
        engine: OcrEngine,
        frames: List<OcrFrame>,
    ): List<OcrFrameText>? = try {
        engine.recognize(frames)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private fun candidates(
        frames: List<OcrFrame>,
        texts: List<OcrFrameText>?,
    ): List<OcrCandidate> = OcrCandidateExtractor.extract(frameData(frames, texts))

    private fun frameData(
        frames: List<OcrFrame>,
        texts: List<OcrFrameText>?,
    ): List<OcrFrameText> {
        val byTimestamp = texts?.associateBy { it.timestampMs }.orEmpty()
        return frames.map { frame ->
            byTimestamp[frame.timestampMs] ?: OcrFrameText(frame.timestampMs, emptyList())
        }
    }

    /**
     * The lines the user can pick from. They come from the pass that won, not
     * from both: a Hebrew pass over Latin text produces confident-looking
     * gibberish ("6אוצה וסצוסא"), and offering that next to the real title is
     * worse than useless. The other pass is only a fallback when the winner
     * read nothing at all.
     */
    private fun rawLines(
        selected: List<OcrFrameText>?,
        other: List<OcrFrameText>?,
    ): List<String> {
        val lines = winningTexts(selected, other).flatMap { frame -> frame.lines.map { it.text } }
        val seen = HashSet<String>()
        return lines.asSequence()
            .map(::clean)
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(MAX_RAW_LINES)
            .toList()
    }

    private fun winningTexts(
        selected: List<OcrFrameText>?,
        other: List<OcrFrameText>?,
    ): List<OcrFrameText> =
        selected?.takeIf { frames -> frames.any { it.lines.isNotEmpty() } } ?: other.orEmpty()

    private fun clean(line: String): String = line.replace(WHITESPACE, " ").trim()

    private fun isPlausible(candidate: OcrCandidate?): Boolean {
        if (candidate == null) return false
        return candidate.title.count { it.isLetter() } >= MIN_TITLE_LETTERS
    }

    private companion object {
        const val MIN_TITLE_LETTERS = 2
        const val MAX_RAW_LINES = 60

        /** The Hebrew pass must be at least this close to win for a Hebrew app. */
        const val HEBREW_MARGIN = 0.92

        /** And clearly better to win for a Latin app. */
        const val LATIN_MARGIN = 1.08

        val WHITESPACE = Regex("[\\s\\u00A0]+")
    }
}
