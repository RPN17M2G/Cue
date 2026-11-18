package io.github.rpn17m2g.cue.domain.ocr

import android.util.Log
import io.github.rpn17m2g.cue.BuildConfig
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
        script: OcrScript = OcrScript.AUTO,
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
                val hebrewOnScreen = hebrewTextFound(hebrewCandidates)
                val useHebrew = when (script) {
                    OcrScript.LATIN -> false
                    OcrScript.HEBREW -> true
                    OcrScript.AUTO -> chooseHebrew(
                        preferHebrew = preferHebrew,
                        latinScore = latinScore,
                        hebrewScore = hebrewScore,
                        hebrewOnScreen = hebrewOnScreen,
                    )
                }
                logChoice(script, useHebrew, latinScore, hebrewScore, latinCandidates, hebrewCandidates)
                logFrames(frames, if (useHebrew) hebrewText else latinText)
                val failed = when (script) {
                    OcrScript.AUTO -> latinText == null && hebrewText == null
                    OcrScript.LATIN -> latinText == null
                    OcrScript.HEBREW -> hebrewText == null
                }

                if (useHebrew) {
                    OcrRecognition(
                        candidates = hebrewCandidates,
                        rawLines = rawLines(hebrewText, latinText),
                        hebrewUsed = true,
                        failed = failed,
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
                        failed = failed,
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
     * The screen's own script decides before the app's language does. ML Kit's
     * Latin recognizer reads a Hebrew title as confident Latin-looking
     * gibberish, so a Hebrew screen in an English app used to lose to
     * "Order now" and "Lyrics" from the player chrome. A real Hebrew reading
     * still has to be clearly stronger there, because a wrong Hebrew guess
     * costs more than a missed one: the user prefers an English reading when
     * the two are close and can force Hebrew from the scan menu.
     *
     * A screen with no real Hebrew word is Latin text: Tesseract reading an
     * English title produces Hebrew-looking strings with digits or Latin
     * letters glued into every word, and those score as high as the correct
     * Latin reading because they are consistently wrong in every frame. The
     * app language is only a tie-break when neither pass found anything.
     */
    private fun chooseHebrew(
        preferHebrew: Boolean,
        latinScore: Double,
        hebrewScore: Double,
        hebrewOnScreen: Boolean,
    ): Boolean = when {
        hebrewScore < 0.0 && latinScore < 0.0 -> preferHebrew
        hebrewScore < 0.0 -> false
        latinScore < 0.0 -> true
        // A Hebrew UI keeps its Hebrew reading even when it is slightly
        // weaker; an English UI needs it to win on its own.
        hebrewOnScreen -> hebrewScore > latinScore * if (preferHebrew) {
            SCRIPT_MARGIN
        } else {
            LATIN_TIE_MARGIN
        }
        else -> hebrewScore > latinScore * LATIN_MARGIN
    }

    /**
     * True when the Hebrew pass's best plausible reading contains a real Hebrew
     * word. A word is a whole whitespace-separated token of Hebrew letters with
     * no digit, Latin letter or other symbol glued in: "תן" and "רונידלומי" are
     * words, while the "סוחוז06)וזסעה+" that Tesseract invents for English text
     * is not, even though punctuation splits it into letter runs.
     */
    private fun hebrewTextFound(candidates: List<OcrCandidate>): Boolean =
        candidates.firstOrNull(::isPlausible)?.let { candidate ->
            hasHebrewWord(candidate.title) || candidate.artist?.let(::hasHebrewWord) == true
        } == true

    private fun hasHebrewWord(text: String): Boolean =
        text.split(WHITESPACE).any(::isHebrewWord)

    private fun isHebrewWord(token: String): Boolean =
        token.length >= 2 &&
            HEBREW_TOKEN.matches(token) &&
            token.count { it in HEBREW_LETTERS } >= 2

    /**
     * One line per scan in debug builds. When a real recording is identified
     * wrongly, this says which pass won and what each engine actually read,
     * which is the difference between a script problem and a lookup problem.
     */
    private fun logChoice(
        script: OcrScript,
        useHebrew: Boolean,
        latinScore: Double,
        hebrewScore: Double,
        latinCandidates: List<OcrCandidate>,
        hebrewCandidates: List<OcrCandidate>,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "ocr script=$script picked=${if (useHebrew) "hebrew" else "latin"} " +
                "latin=${latinScore.format()}(${reading(latinCandidates)}) " +
                "hebrew=${hebrewScore.format()}(${reading(hebrewCandidates)})",
        )
        Log.d(TAG, "latin top: ${top(latinCandidates)}")
        Log.d(TAG, "hebrew top: ${top(hebrewCandidates)}")
    }

    /**
     * Every line of every frame with its box, one log line per frame. When a
     * marquee or a stacked player layout is read wrongly this is the only way
     * to see whether the text was missed or merely ranked badly.
     */
    private fun logFrames(frames: List<OcrFrame>, texts: List<OcrFrameText>?) {
        if (!BuildConfig.DEBUG) return
        val byTimestamp = texts?.associateBy { it.timestampMs }.orEmpty()
        frames.forEach { frame ->
            val lines = byTimestamp[frame.timestampMs]?.lines.orEmpty()
            val rendered = lines.joinToString(" | ") { line ->
                val box = line.box?.let {
                    String.format(
                        java.util.Locale.ROOT,
                        "%.2f,%.2f,%.2f,%.2f",
                        it.left,
                        it.top,
                        it.right,
                        it.bottom,
                    )
                } ?: "?"
                "[$box]${line.text}"
            }
            Log.d(TAG, "frame ${frame.timestampMs}ms: $rendered")
        }
    }

    private fun top(candidates: List<OcrCandidate>): String =
        candidates.take(MAX_LOGGED_CANDIDATES).joinToString(" | ") { candidate ->
            val artist = candidate.artist?.let { " - $it" }.orEmpty()
            "${candidate.score.format()}${candidate.title}$artist"
        }

    private fun reading(candidates: List<OcrCandidate>): String =
        candidates.firstOrNull()?.let { candidate ->
            candidate.artist?.let { "${it} - ${candidate.title}" } ?: candidate.title
        }.orEmpty()

    private fun Double.format(): String =
        if (this < 0.0) "-" else String.format(java.util.Locale.ROOT, "%.2f", this)

    /**
     * The best plausible score, or a negative value when nothing is plausible.
     * A high-scoring line with no letters (a phone number, a counter) can sit
     * above the real title, so the first plausible candidate decides, not the
     * first candidate.
     */
    private fun strongScore(candidates: List<OcrCandidate>): Double =
        candidates.firstOrNull(::isPlausible)?.score ?: -1.0

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
        const val TAG = "CueOcr"
        const val MIN_TITLE_LETTERS = 2
        const val MAX_RAW_LINES = 60
        const val MAX_LOGGED_CANDIDATES = 6

        /** The Hebrew pass must be at least this close in a Hebrew UI. */
        const val SCRIPT_MARGIN = 0.80

        /** In an English UI the Hebrew pass has to win outright. */
        const val LATIN_TIE_MARGIN = 1.0

        /** And clearly better to win when the screen shows no Hebrew. */
        const val LATIN_MARGIN = 1.08

        /** A whole token of Hebrew letters, marks and geresh only. */
        val HEBREW_TOKEN = Regex("^[\\u0590-\\u05FF\\uFB1D-\\uFB4F]+$")
        val HEBREW_LETTERS = 'א'..'ת'
        val WHITESPACE = Regex("[\\s\\u00A0]+")
    }
}
