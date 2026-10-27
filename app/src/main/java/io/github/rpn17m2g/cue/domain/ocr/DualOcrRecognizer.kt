package io.github.rpn17m2g.cue.domain.ocr

import kotlinx.coroutines.CancellationException

data class OcrRecognition(
    val candidates: List<OcrCandidate>,
    val rawLines: List<String>,
    val hebrewUsed: Boolean,
    val failed: Boolean,
)

/**
 * Runs the Latin engine first and only spends a Hebrew pass when the app is in
 * Hebrew or the Latin reading is weak. The pass with the better reading wins; a
 * missing or failing engine degrades to the other one.
 */
class DualOcrRecognizer(
    private val latin: OcrEngine,
    private val hebrew: OcrEngine,
) {

    suspend fun recognize(
        frames: List<OcrFrame>,
        preferHebrew: Boolean,
    ): OcrRecognition {
        if (frames.isEmpty()) {
            return OcrRecognition(emptyList(), emptyList(), hebrewUsed = false, failed = true)
        }

        val latinText = readSafely(latin, frames)
        val latinCandidates = candidates(frames, latinText)
        val latinScore = strongScore(latinCandidates)

        if (!preferHebrew && latinScore >= STRONG_LATIN_READING) {
            return OcrRecognition(
                candidates = latinCandidates,
                rawLines = rawLines(latinText, null),
                hebrewUsed = false,
                failed = false,
            )
        }

        val hebrewText = readSafely(hebrew, frames)
        val hebrewCandidates = candidates(frames, hebrewText)
        val hebrewScore = strongScore(hebrewCandidates)
        val useHebrew = chooseHebrew(preferHebrew, latinScore, hebrewScore)

        return if (useHebrew) {
            OcrRecognition(
                candidates = hebrewCandidates,
                rawLines = rawLines(hebrewText, latinText),
                hebrewUsed = true,
                failed = latinText == null && hebrewText == null,
            )
        } else {
            OcrRecognition(
                candidates = latinCandidates,
                rawLines = rawLines(latinText, hebrewText),
                hebrewUsed = false,
                failed = latinText == null && hebrewText == null,
            )
        }
    }

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

    private fun rawLines(
        selected: List<OcrFrameText>?,
        other: List<OcrFrameText>?,
    ): List<String> {
        val lines = buildList {
            selected.orEmpty().forEach { frame -> frame.lines.forEach { add(it.text) } }
            other.orEmpty().forEach { frame -> frame.lines.forEach { add(it.text) } }
        }
        val seen = HashSet<String>()
        return lines.asSequence()
            .map(::clean)
            .filter { it.isNotEmpty() && seen.add(it.lowercase()) }
            .take(MAX_RAW_LINES)
            .toList()
    }

    private fun clean(line: String): String = line.replace(WHITESPACE, " ").trim()

    private fun isPlausible(candidate: OcrCandidate?): Boolean {
        if (candidate == null) return false
        return candidate.title.count { it.isLetter() } >= MIN_TITLE_LETTERS
    }

    private companion object {
        const val MIN_TITLE_LETTERS = 2
        const val MAX_RAW_LINES = 60

        /** A Latin reading this good does not need the Hebrew pass. */
        const val STRONG_LATIN_READING = 0.50

        /** The Hebrew pass must be at least this close to win for a Hebrew app. */
        const val HEBREW_MARGIN = 0.92

        /** And clearly better to win for a Latin app. */
        const val LATIN_MARGIN = 1.08

        val WHITESPACE = Regex("[\\s\\u00A0]+")
    }
}
