package io.github.rpn17m2g.cue.domain.ocr

import java.util.Locale

/**
 * Turns the lines read from sampled frames into ranked song candidates.
 *
 * Screen recordings are noisy: the status bar, the player's own chrome
 * ("Now playing", "Up next") and notifications all carry text, and OCR returns
 * lines in detection order rather than top-to-bottom order. The aggregator
 * therefore works on geometry rather than adjacency:
 *
 * - Chrome is dropped by pattern (dates, clocks, battery, percentages, domains)
 *   and by a noise phrase list.
 * - Each remaining line is scored by how often it survives across frames, how
 *   large it is inside its frame, and how centered it is, because a player shows
 *   the title as the biggest, steadiest, usually centered text. Size is measured
 *   relative to the frame's own largest line, so resolution does not matter.
 * - An artist is only paired with a title when the line sits directly beneath
 *   it, overlaps it horizontally and is not larger, so a date at the top of the
 *   screen can never become the artist of a label in the middle.
 * - A single "Artist - Title" line is split, which is how notifications and
 *   YouTube present tracks.
 *
 * Scores combine stability, quality and the presence of an artist:
 * `0.45 * stability + 0.45 * bestQuality + 0.1 * (artist ? 1 : 0)`, capped at
 * `1.0`. Equal scores are ordered by normalized title, then artist.
 */
object OcrCandidateExtractor {

    const val MAX_LINE_LENGTH = 120

    /** Weight of the candidate's best per-frame quality versus its stability. */
    private const val STABILITY_WEIGHT = 0.45
    private const val QUALITY_WEIGHT = 0.45
    private const val ARTIST_WEIGHT = 0.10

    /** A paired line must sit within this many title heights below the title. */
    private const val MAX_ARTIST_GAP_IN_HEIGHTS = 1.8f

    /** And must not be this much taller than the title it belongs to. */
    private const val MAX_ARTIST_HEIGHT_RATIO = 1.15f

    /** And must overlap the title horizontally by at least this fraction. */
    private const val MIN_HORIZONTAL_OVERLAP = 0.35f

    /** Used when an engine cannot report geometry. */
    private const val UNKNOWN_PROMINENCE = 0.45f

    val DEFAULT_NOISE_PHRASES: Set<String> = setOf(
        // Player chrome.
        "Now playing",
        "Now playing from",
        "Playing from",
        "Playing next",
        "Up next",
        "Next up",
        "Shuffle",
        "Shuffle play",
        "Repeat",
        "Repeat all",
        "Repeat one",
        "Cast",
        "Sleep timer",
        "Lyrics",
        "Queue",
        "Explicit",
        "Add to playlist",
        "Added to playlist",
        "Add to queue",
        "Play next",
        "Save to library",
        "Save to your library",
        "Saved",
        "Like",
        "Liked",
        "Share",
        "Download",
        "Downloaded",
        "More options",
        // Navigation and shelves.
        "Home",
        "Search",
        "Explore",
        "Library",
        "Your library",
        "Browse",
        "Radio",
        "Podcasts",
        "Videos",
        "Albums",
        "Artists",
        "Songs",
        "Playlists",
        "Recently played",
        "Made for you",
        "Recommended",
        "Watch next",
        "Autoplay",
        "Chapters",
        "Transcript",
        "Show more",
        "See all",
        "Sign in",
        "Sign up",
        "Log in",
        "Upgrade",
        "Premium",
        "Subscribe",
        "Sponsored",
        "Advertisement",
        "Install",
        "Open",
        "Close",
        "Cancel",
        "Done",
        "Skip",
        "Back",
        "Next",
        "Previous",
    )

    private val WHITESPACE = Regex("[\\s\\u00A0]+")
    private val MERIDIEM = Regex("\\d{1,2}:\\d{2}(:\\d{2})?\\s*[AaPp][Mm]")
    private val DURATION = Regex("^\\d{1,2}:\\d{2}:\\d{2}$")
    private val BARE_CLOCK = Regex("^\\d{1,2}:\\d{2}$")
    private val PERCENT_ANY = Regex("%")
    private val STATUS_HINT = Regex(
        "(?i)\\b(?:wi-?fi|volte?|lte|5g|4g|3g|bluetooth|battery|charging|alarm)\\b",
    )
    private val DOMAIN = Regex(
        "(?i)(?:[a-z0-9-]+\\.)+" +
            "(?:com|net|org|io|co|me|app|dev|ly|gl|be|music|youtube|spotify|link|page)" +
            "(?![a-z0-9-])(?:[/:?#]\\S*)?",
    )
    private val MONTHS =
        "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|" +
            "aug(?:ust)?|sep(?:t|tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"
    private val DATE_WITH_MONTH = Regex(
        "(?i)^(?:$MONTHS)\\s+\\d{1,2}(?:st|nd|rd|th)?(?:,?\\s*\\d{2,4})?$|" +
            "^\\d{1,2}(?:st|nd|rd|th)?\\s+(?:$MONTHS)(?:,?\\s*\\d{2,4})?$|" +
            "^(?:$MONTHS),?\\s*\\d{2,4}$",
    )
    private val NUMERIC_DATE = Regex("^\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4}\\.?$")
    private val HAS_LETTER_OR_DIGIT = Regex("[\\p{L}\\p{Nd}]")
    private val HEBREW = Regex("[\\u0590-\\u05FF\\uFB1D-\\uFB4F]")
    private val SEPARATOR = Regex("\\s+[-\\u2013\\u2014\\u2022|]\\s+")
    private val DEFAULT_NOISE_KEYS: Set<String> =
        DEFAULT_NOISE_PHRASES.mapTo(HashSet(), ::normalize)

    fun extract(
        frames: List<OcrFrameText>,
        noisePhrases: Set<String> = emptySet(),
        hebrewAware: Boolean = true,
    ): List<OcrCandidate> {
        if (frames.isEmpty()) return emptyList()

        val noiseKeys = (DEFAULT_NOISE_PHRASES + noisePhrases)
            .mapTo(HashSet()) { normalize(it) }
        val stats = linkedMapOf<CandidateKey, CandidateStat>()
        val totalFrames = frames.size.toDouble()

        frames.forEach { frame ->
            val hasStatusClock = frame.lines.any { line ->
                val text = clean(line.text).orEmpty()
                MERIDIEM.containsMatchIn(text) || DURATION.matches(text)
            }
            val frameLargest = frame.lines
                .mapNotNull { it.box?.height }
                .filter { it > 0f }
                .maxOrNull() ?: 0f
            val kept = frame.lines
                .mapNotNull { line ->
                    keep(line, noiseKeys, hebrewAware, hasStatusClock, frameLargest)
                }
            if (kept.isEmpty()) return@forEach

            val largest = kept.maxOf { it.prominence }
            val seen = HashSet<CandidateKey>()
            kept.forEachIndexed { index, line ->
                val quality = qualityOf(line, largest)
                val artist = line.inlineArtist ?: bestArtist(kept, index)?.text
                if (artist != null) {
                    val pairKey = CandidateKey(normalize(line.text), normalize(artist))
                    if (seen.add(pairKey)) {
                        record(stats, pairKey, line.text, artist, quality)
                    }
                }
                val soloKey = CandidateKey(normalize(line.text), null)
                if (seen.add(soloKey)) {
                    record(stats, soloKey, line.text, null, quality * SOLO_QUALITY_PENALTY)
                }
            }
        }

        return stats.values
            .map { stat ->
                val stability = stat.frameCount / totalFrames
                val base = STABILITY_WEIGHT * stability + QUALITY_WEIGHT * stat.bestQuality
                val artistBonus = if (stat.artist != null) ARTIST_WEIGHT else 0.0
                OcrCandidate(
                    title = stat.title,
                    artist = stat.artist,
                    score = (base + artistBonus).coerceAtMost(1.0),
                )
            }
            .sortedWith(
                compareByDescending<OcrCandidate> { it.score }
                    .thenBy { normalize(it.title) }
                    .thenBy { it.artist?.let(::normalize).orEmpty() },
            )
    }

    private fun record(
        stats: MutableMap<CandidateKey, CandidateStat>,
        key: CandidateKey,
        title: String,
        artist: String?,
        quality: Double,
    ) {
        val existing = stats[key]
        stats[key] = existing?.copy(
            frameCount = existing.frameCount + 1,
            bestQuality = maxOf(existing.bestQuality, quality),
        ) ?: CandidateStat(title, artist, 1, quality)
    }

    /** Drops chrome and keeps the geometry the ranking needs. */
    private fun keep(
        line: OcrLine,
        noiseKeys: Set<String>,
        hebrewAware: Boolean,
        hasStatusClock: Boolean,
        frameLargest: Float,
    ): KeptLine? {
        val text = clean(line.text) ?: return null
        if (text.length > MAX_LINE_LENGTH) return null
        if (text.length == 1) return null
        if (!HAS_LETTER_OR_DIGIT.containsMatchIn(text)) return null
        if (!hebrewAware && HEBREW.containsMatchIn(text)) return null

        val box = line.box
        val prominence = when {
            box == null -> UNKNOWN_PROMINENCE
            box.height <= 0f -> UNKNOWN_PROMINENCE
            else -> box.height
        }
        if (isChrome(text, noiseKeys, hasStatusClock, prominence, frameLargest)) return null

        val inline = splitInline(text)
        return KeptLine(
            text = inline?.title ?: text,
            inlineArtist = inline?.artist,
            box = box,
            prominence = prominence,
        )
    }

    private fun isChrome(
        text: String,
        noiseKeys: Set<String>,
        hasStatusClock: Boolean,
        prominence: Float,
        frameLargest: Float,
    ): Boolean {
        val normalized = normalize(text)
        if (normalized in noiseKeys && !isTitleSized(normalized, prominence, frameLargest)) {
            return true
        }
        if (MERIDIEM.containsMatchIn(text)) return true
        if (DURATION.matches(text)) return true
        if (hasStatusClock && BARE_CLOCK.matches(text)) return true
        if (PERCENT_ANY.containsMatchIn(text)) return true
        if (STATUS_HINT.containsMatchIn(text)) return true
        if (DOMAIN.containsMatchIn(text)) return true
        if (DATE_WITH_MONTH.matches(text)) return true
        if (NUMERIC_DATE.matches(text)) return true
        return false
    }

    /**
     * A single-word noise phrase ("Home", "Like", "Download") is also a
     * plausible song title. It is only treated as chrome when it is not the
     * title-sized text of its frame; "NOW PLAYING" and other multi-word chrome
     * labels are always dropped.
     */
    private fun isTitleSized(
        normalized: String,
        prominence: Float,
        frameLargest: Float,
    ): Boolean {
        if (normalized.contains(' ')) return false
        if (frameLargest <= 0f) return false
        return prominence >= frameLargest * SINGLE_WORD_TITLE_RATIO
    }

    /**
     * Splits the common one-line "Artist - Title" form used by notifications
     * and by YouTube, where the two fields share a single text line. The artist
     * is the left half, matching the way the earlier dash parser read it.
     */
    private fun splitInline(text: String): InlinePair? {
        val parts = text.split(SEPARATOR)
        if (parts.size != 2) return null
        val artist = parts[0].trim()
        val title = parts[1].trim()
        if (artist.length < 2 || title.length < 2) return null
        if (normalize(artist) in DEFAULT_NOISE_KEYS) return null
        return InlinePair(artist = artist, title = title)
    }

    /**
     * Largest text in the frame is the anchor: a title normally is the biggest
     * thing on the screen, while status bars, labels and notifications are not.
     */
    private fun qualityOf(line: KeptLine, largest: Float): Double {
        val prominence = if (largest <= 0f) UNKNOWN_PROMINENCE else line.prominence / largest
        val centered = line.box?.let { box ->
            (1.0 - (kotlin.math.abs(box.centerX - 0.5f) / 0.5f).coerceIn(0f, 1f).toDouble())
        } ?: 0.5
        return (0.7 * prominence.coerceIn(0f, 1f) + 0.3 * centered).coerceIn(0.0, 1.0)
    }

    /** The line directly under [index] that plausibly names the artist. */
    private fun bestArtist(kept: List<KeptLine>, index: Int): KeptLine? {
        val title = kept[index]
        val titleBox = title.box ?: return null
        var best: KeptLine? = null
        var bestGap = Float.MAX_VALUE

        for (candidateIndex in kept.indices) {
            if (candidateIndex == index) continue
            val candidate = kept[candidateIndex]
            val box = candidate.box ?: continue
            if (box.top < titleBox.bottom - titleBox.height * 0.25f) continue
            val gap = (box.top - titleBox.bottom).coerceAtLeast(0f)
            if (gap > titleBox.height * MAX_ARTIST_GAP_IN_HEIGHTS) continue
            if (box.height > titleBox.height * MAX_ARTIST_HEIGHT_RATIO) continue
            if (titleBox.horizontalOverlap(box) < MIN_HORIZONTAL_OVERLAP) continue
            if (gap < bestGap) {
                bestGap = gap
                best = candidate
            }
        }
        return best
    }

    private fun clean(value: String): String? {
        val collapsed = value.replace(WHITESPACE, " ").trim()
        return collapsed.ifEmpty { null }
    }

    private fun normalize(value: String): String =
        value.replace(WHITESPACE, " ").trim().lowercase(Locale.ROOT)

    private data class KeptLine(
        val text: String,
        val inlineArtist: String?,
        val box: OcrBox?,
        val prominence: Float,
    )

    private data class InlinePair(val artist: String, val title: String)

    private data class CandidateKey(val title: String, val artist: String?)

    private data class CandidateStat(
        val title: String,
        val artist: String?,
        val frameCount: Int,
        val bestQuality: Double,
    )

    private const val SOLO_QUALITY_PENALTY = 0.9

    /** Single-word chrome is dropped unless it is at least this big. */
    private const val SINGLE_WORD_TITLE_RATIO = 0.6f
}
