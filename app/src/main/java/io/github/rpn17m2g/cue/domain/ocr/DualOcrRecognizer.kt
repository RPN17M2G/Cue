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
 * Hebrew or the Latin reading did not produce a plausible title. The pass with
 * the better reading wins; a missing or failing engine degrades to the other one.
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
        val latinTitle = latinCandidates.firstOrNull()

        if (!preferHebrew && isPlausible(latinTitle)) {
            return OcrRecognition(
                candidates = latinCandidates,
                rawLines = rawLines(latinText, null),
                hebrewUsed = false,
                failed = false,
            )
        }

        val hebrewText = readSafely(hebrew, frames)
        val hebrewCandidates = candidates(frames, hebrewText)
        val hebrewTitle = hebrewCandidates.firstOrNull()
        val useHebrew = when {
            preferHebrew -> isPlausible(hebrewTitle) || !isPlausible(latinTitle)
            else -> isPlausible(hebrewTitle) || latinCandidates.isEmpty()
        }

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
    ): List<OcrCandidate> = OcrCandidateExtractor.extract(frameLines(frames, texts))

    private fun frameLines(
        frames: List<OcrFrame>,
        texts: List<OcrFrameText>?,
    ): List<List<String>> {
        val byTimestamp = texts?.associateBy { it.timestampMs }.orEmpty()
        return frames.map { frame -> byTimestamp[frame.timestampMs]?.lines.orEmpty() }
    }

    private fun rawLines(
        selected: List<OcrFrameText>?,
        other: List<OcrFrameText>?,
    ): List<String> {
        val lines = buildList {
            selected.orEmpty().forEach { addAll(it.lines) }
            other.orEmpty().forEach { addAll(it.lines) }
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
        val WHITESPACE = Regex("[\\s\\u00A0]+")
    }
}
