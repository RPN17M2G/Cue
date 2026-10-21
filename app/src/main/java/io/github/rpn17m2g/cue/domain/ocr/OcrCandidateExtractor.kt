package io.github.rpn17m2g.cue.domain.ocr

import java.util.Locale

/**
 * Aggregates OCR lines sampled from several frames into ranked song candidates.
 *
 * Scores are deterministic: `0.999 * frameSupport + artistBonus`, where
 * `frameSupport` is the fraction of frames containing the candidate and
 * `artistBonus` is `0.001` when an artist was parsed. A candidate seen in every
 * frame therefore scores between `0.999` and `1.0`, and support always outranks
 * the bonus. Ties are ordered by normalized title, then artist.
 */
object OcrCandidateExtractor {

    const val MAX_LINE_LENGTH = 120

    val DEFAULT_NOISE_PHRASES: Set<String> = setOf(
        "Up next",
        "Shuffle",
        "Library",
        "Home",
        "Search",
        "Explore",
        "Sign in",
        "Add to playlist",
        "Play next",
        "Save to library",
    )

    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val MERIDIEM = Regex("\\d{1,2}:\\d{2}(:\\d{2})?\\s*[AaPp][Mm]")
    private val DURATION = Regex("^\\d{1,2}:\\d{2}:\\d{2}$")
    private val BARE_CLOCK = Regex("^\\d{1,2}:\\d{2}$")
    private val PERCENT = Regex("^\\d{1,3}%$")
    private val BATTERY = Regex(".*\\b(?:battery|charging)\\b.*\\d{1,3}%", RegexOption.IGNORE_CASE)
    private val URL = Regex("(?i)(?:https?://|www\\.)\\S+")
    private val BARE_DOMAIN = Regex(
        "(?i)^(?:[a-z0-9-]+\\.){2,}(?:com|net|org|io|il|co|uk|de|fr|ru|tv|me|app|dev|info|biz|edu|gov)$",
    )
    private val HAS_LETTER_OR_DIGIT = Regex("[\\p{L}\\p{Nd}]")
    private val HEBREW = Regex("[\\u0590-\\u05FF\\uFB1D-\\uFB4F]")
    private val SEPARATOR = Regex("\\s+[-\\u2013\\u2014\\u2022|]\\s+")

    fun extract(
        frames: List<List<String>>,
        noisePhrases: Set<String> = emptySet(),
        hebrewAware: Boolean = true,
    ): List<OcrCandidate> {
        if (frames.isEmpty()) return emptyList()

        val noiseKeys = (DEFAULT_NOISE_PHRASES + noisePhrases)
            .mapTo(HashSet()) { normalize(it) }
        val stats = linkedMapOf<CandidateKey, CandidateStat>()

        frames.forEach { frame ->
            val cleaned = frame.mapNotNull(::clean)
            val statusContext = cleaned.any(::isStatusIndicator)
            val lines = cleaned.filterNot { isNoise(it, noiseKeys, hebrewAware, statusContext) }
            val seenInFrame = HashSet<CandidateKey>()

            fun record(title: String, artist: String?) {
                val cleanTitle = clean(title) ?: return
                val cleanArtist = artist?.let(::clean)?.takeIf { it.isNotEmpty() }
                val key = CandidateKey(normalize(cleanTitle), cleanArtist?.let(::normalize))
                if (!seenInFrame.add(key)) return
                val existing = stats[key]
                stats[key] = existing?.copy(frameCount = existing.frameCount + 1)
                    ?: CandidateStat(cleanTitle, cleanArtist, 1)
            }

            var previousWasPaired = false
            lines.forEachIndexed { index, line ->
                val parsed = parseArtistTitle(line)
                if (parsed != null) {
                    record(parsed.title, parsed.artist)
                    previousWasPaired = false
                } else {
                    record(line, null)
                    val next = lines.getOrNull(index + 1)
                    if (!previousWasPaired && next != null && parseArtistTitle(next) == null) {
                        record(line, next)
                        previousWasPaired = true
                    } else {
                        previousWasPaired = false
                    }
                }
            }
        }

        val totalFrames = frames.size.toDouble()
        return stats.values
            .map { stat ->
                val support = stat.frameCount / totalFrames
                val bonus = if (stat.artist != null) 0.001 else 0.0
                OcrCandidate(stat.title, stat.artist, score = 0.999 * support + bonus)
            }
            .sortedWith(
                compareByDescending<OcrCandidate> { it.score }
                    .thenBy { normalize(it.title) }
                    .thenBy { it.artist?.let(::normalize).orEmpty() },
            )
    }

    private fun clean(value: String): String? {
        val collapsed = value.replace(WHITESPACE, " ").trim()
        return collapsed.ifEmpty { null }
    }

    private fun normalize(value: String): String =
        value.replace(WHITESPACE, " ").trim().lowercase(Locale.ROOT)

    private fun isNoise(
        line: String,
        noiseKeys: Set<String>,
        hebrewAware: Boolean,
        statusContext: Boolean,
    ): Boolean {
        if (line.length > MAX_LINE_LENGTH) return true
        if (line.length == 1) return true
        if (!HAS_LETTER_OR_DIGIT.containsMatchIn(line)) return true
        if (!hebrewAware && HEBREW.containsMatchIn(line)) return true
        if (normalize(line) in noiseKeys) return true
        if (MERIDIEM.containsMatchIn(line)) return true
        if (DURATION.matches(line)) return true
        if (BARE_CLOCK.matches(line) && statusContext) return true
        if (PERCENT.matches(line)) return true
        if (BATTERY.matches(line)) return true
        if (URL.containsMatchIn(line)) return true
        if (BARE_DOMAIN.matches(line)) return true
        return false
    }

    private fun isStatusIndicator(line: String): Boolean =
        MERIDIEM.containsMatchIn(line) ||
            DURATION.matches(line) ||
            PERCENT.matches(line) ||
            BATTERY.matches(line)

    private fun parseArtistTitle(line: String): ParsedLine? {
        val match = SEPARATOR.find(line) ?: return null
        val artist = line.substring(0, match.range.first).trim()
        val title = line.substring(match.range.last + 1).trim()
        if (artist.isEmpty() || title.isEmpty()) return null
        return ParsedLine(title = title, artist = artist)
    }

    private data class ParsedLine(val title: String, val artist: String)

    private data class CandidateKey(val title: String, val artist: String?)

    private data class CandidateStat(val title: String, val artist: String?, val frameCount: Int)
}
