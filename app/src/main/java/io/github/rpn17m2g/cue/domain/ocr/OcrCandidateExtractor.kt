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
 * `1.0`. Stability is a time-weighted vote: each frame contributes the interval
 * it represents, so a candidate held on screen for a long stretch outranks one
 * that flashed past, even when the sampler skipped frames. A scrolling marquee
 * title is cut differently in every frame, so fragments of the same line are
 * stitched into the longest reading before the vote is counted. Scores are
 * compared at three decimals and near-ties prefer the top of a stacked
 * title/artist/album block, then the normalized title, then artist.
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
        val weights = frameWeights(frames)
        val totalWeight = weights.sum().takeIf { it > 0.0 } ?: frames.size.toDouble()

        frames.forEachIndexed { frameIndex, frame ->
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
            if (kept.isEmpty()) return@forEachIndexed

            val weight = weights[frameIndex]
            val largest = kept.maxOf { it.prominence }
            val seen = HashSet<CandidateKey>()
            kept.forEachIndexed { index, line ->
                val quality = qualityOf(line, largest)
                val subtitle = looksLikeSubtitle(kept, index)
                val artist = line.inlineArtist ?: bestArtist(kept, index)?.text
                if (artist != null) {
                    val pairKey = CandidateKey(normalize(line.text), normalize(artist))
                    if (seen.add(pairKey)) {
                        record(stats, pairKey, line.text, artist, quality, subtitle, frameIndex, weight)
                    }
                }
                val soloKey = CandidateKey(normalize(line.text), null)
                if (seen.add(soloKey)) {
                    record(
                        stats,
                        soloKey,
                        line.text,
                        null,
                        quality * SOLO_QUALITY_PENALTY,
                        subtitle,
                        frameIndex,
                        weight,
                    )
                }
            }
        }

        return mergeFragments(stats.values)
            .map { stat ->
                val stability = (stat.weight / totalWeight).coerceIn(0.0, 1.0)
                val base = STABILITY_WEIGHT * stability + QUALITY_WEIGHT * stat.bestQuality
                val artistBonus = if (stat.artist != null) ARTIST_WEIGHT else 0.0
                stat to OcrCandidate(
                    title = stat.title,
                    artist = stat.artist,
                    score = (base + artistBonus).coerceAtMost(1.0),
                )
            }
            .sortedWith(
                compareByDescending<Pair<CandidateStat, OcrCandidate>> { (_, candidate) ->
                    kotlin.math.round(candidate.score * SCORE_TIE_PRECISION)
                }
                    .thenBy { (stat, _) -> stat.alwaysSubtitle }
                    .thenBy { (_, candidate) -> normalize(candidate.title) }
                    .thenBy { (_, candidate) -> candidate.artist?.let(::normalize).orEmpty() },
            )
            .map { (_, candidate) -> candidate }
    }

    /**
     * How much of the recording each frame stands for. Timestamps are normally
     * evenly spaced, but OCR can skip a frame that failed to decode, and the
     * sampler may be tuned later; weighting by the gap keeps the vote honest in
     * both cases. The final frame gets the median gap because its own end is
     * unknown.
     */
    private fun frameWeights(frames: List<OcrFrameText>): DoubleArray {
        if (frames.isEmpty()) return DoubleArray(0)
        val gaps = frames.zipWithNext { current, next ->
            next.timestampMs - current.timestampMs
        }
        val positive = gaps.filter { it > 0L }
        // Every frame at the same instant (or a single frame) carries the same
        // vote, matching the old frame-count ranking.
        if (positive.isEmpty()) return DoubleArray(frames.size) { 1.0 }
        val fallback = positive.sorted()[positive.size / 2].toDouble()
        return DoubleArray(frames.size) { index ->
            if (index == frames.lastIndex) {
                fallback
            } else {
                // A repeated or out-of-order timestamp must not claim negative
                // time; the frame simply stands for no interval.
                gaps[index].coerceAtLeast(0L).toDouble()
            }
        }
    }

    /**
     * A marquee title scrolls across the line, so consecutive frames read
     * different slices of it ("Get Luc", "et Lucky", "Get Lucky"). Each slice is
     * its own candidate key, and on its own every one looks unstable. Folding a
     * fragment into the reading that was on screen longest restores the vote the
     * title earned and keeps the complete text. A longer line that merely
     * contains the title ("Get Lucky Official Video") is not swallowed unless it
     * is a comparable slice of it, and a fragment recorded in the same frame as
     * the host adds no time: the two are one sighting.
     *
     * Containment needs at least one frame that saw the whole line. A marquee
     * that is never fully visible cannot be reconstructed here; the longest
     * slice stands for the title.
     */
    private fun mergeFragments(stats: Collection<CandidateStat>): List<CandidateStat> {
        val ordered = stats.sortedWith(
            compareByDescending<CandidateStat> { it.weight }
                .thenByDescending { titleKey(it.title).length }
                .thenByDescending { it.bestQuality }
                .thenBy { titleKey(it.title) },
        )
        val merged = ArrayList<CandidateStat>(ordered.size)
        ordered.forEach { stat ->
            val hostIndex = merged.indexOfFirst { host -> canMerge(host, stat) }
            if (hostIndex >= 0) {
                val host = merged[hostIndex]
                merged[hostIndex] = host.copy(
                    artist = host.artist ?: stat.artist,
                    frameWeights = unionWeights(host.frameWeights, stat.frameWeights),
                    bestQuality = maxOf(host.bestQuality, stat.bestQuality),
                )
            } else {
                merged += stat
            }
        }
        return merged
    }

    /** A frame is one sighting however many ways the same line was keyed. */
    private fun unionWeights(first: Map<Int, Double>, second: Map<Int, Double>): Map<Int, Double> {
        if (first.isEmpty()) return second
        if (second.isEmpty()) return first
        val union = first.toMutableMap()
        second.forEach { (frame, weight) -> union[frame] = maxOf(union[frame] ?: 0.0, weight) }
        return union
    }

    private fun canMerge(host: CandidateStat, fragment: CandidateStat): Boolean {
        val hostTitle = titleKey(host.title)
        val fragmentTitle = titleKey(fragment.title)
        if (fragmentTitle.length < MIN_FRAGMENT_LENGTH) return false
        val shorter = minOf(hostTitle.length, fragmentTitle.length)
        val longer = maxOf(hostTitle.length, fragmentTitle.length)
        if (shorter.toDouble() / longer < MIN_FRAGMENT_RATIO) return false
        if (!hostTitle.contains(fragmentTitle) && !fragmentTitle.contains(hostTitle)) return false
        val hostArtist = host.artist?.let(::normalize)
        val fragmentArtist = fragment.artist?.let(::normalize)
        return hostArtist == null || fragmentArtist == null || hostArtist == fragmentArtist
    }

    /** Letters and digits only, so punctuation and spacing cannot split a title. */
    private fun titleKey(value: String): String =
        value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun record(
        stats: MutableMap<CandidateKey, CandidateStat>,
        key: CandidateKey,
        title: String,
        artist: String?,
        quality: Double,
        subtitle: Boolean,
        frameIndex: Int,
        weight: Double,
    ) {
        val existing = stats[key]
        stats[key] = existing?.copy(
            frameWeights = existing.frameWeights + (frameIndex to weight),
            bestQuality = maxOf(existing.bestQuality, quality),
            alwaysSubtitle = existing.alwaysSubtitle && subtitle,
        ) ?: CandidateStat(
            title = title,
            artist = artist,
            frameWeights = mapOf(frameIndex to weight),
            bestQuality = quality,
            alwaysSubtitle = subtitle,
        )
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
        if (isStatusBarLine(box, prominence, frameLargest)) return null

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
     * The status bar is the one strip every screen recording carries. Its text
     * is a clock, a date and connectivity badges: in Hebrew that is "יום ד׳ 28",
     * which no locale-independent date pattern catches. Anything small that
     * sits entirely inside the top band of the frame is therefore chrome, while
     * a title the player draws near the top edge is large enough to survive.
     */
    private fun isStatusBarLine(
        box: OcrBox?,
        prominence: Float,
        frameLargest: Float,
    ): Boolean {
        if (box == null || box.bottom > STATUS_BAR_BAND) return false
        if (frameLargest <= 0f) return true
        return prominence < frameLargest * STATUS_BAR_MAX_PROMINENCE
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
     * True when a line of similar size sits directly above [index]. A player
     * stacks title, artist and album on top of each other, so every line but
     * the first of that stack looks like somebody's subtitle. Hebrew letters
     * with ascenders make the boxes of those lines nearly the same height, so
     * this breaks the resulting near-ties in favour of the top of the stack.
     */
    private fun looksLikeSubtitle(kept: List<KeptLine>, index: Int): Boolean {
        val line = kept[index]
        val box = line.box ?: return false
        for (otherIndex in kept.indices) {
            if (otherIndex == index) continue
            val other = kept[otherIndex]
            val otherBox = other.box ?: continue
            if (otherBox.bottom > box.top + box.height * 0.25f) continue
            if (otherBox.height < box.height * MIN_TITLE_STACK_RATIO) continue
            if (otherBox.horizontalOverlap(box) < MIN_HORIZONTAL_OVERLAP) continue
            val gap = (box.top - otherBox.bottom).coerceAtLeast(0f)
            if (gap > box.height * MAX_ARTIST_GAP_IN_HEIGHTS) continue
            return true
        }
        return false
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
    private fun bestArtist(kept: List<KeptLine>, index: Int): KeptLine? {        val title = kept[index]
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
        val frameWeights: Map<Int, Double>,
        val bestQuality: Double,
        val alwaysSubtitle: Boolean,
    ) {
        val weight: Double get() = frameWeights.values.sum()
    }

    private const val SOLO_QUALITY_PENALTY = 0.9

    /** Shorter fragments than this are too ambiguous to stitch. */
    private const val MIN_FRAGMENT_LENGTH = 4

    /**
     * A fragment must cover this much of the longer reading. A title and a queue
     * row that adds "Official Video" are too far apart to be the same sighting.
     */
    private const val MIN_FRAGMENT_RATIO = 0.5

    /** Single-word chrome is dropped unless it is at least this big. */
    private const val SINGLE_WORD_TITLE_RATIO = 0.6f

    /** Lines that end above this fraction of the frame are in the status bar. */
    private const val STATUS_BAR_BAND = 0.06f

    /** A status-bar line is never the big text of its frame. */
    private const val STATUS_BAR_MAX_PROMINENCE = 0.45f

    /** Scores are compared at this precision, so near-ties fall to the layout. */
    private const val SCORE_TIE_PRECISION = 1000.0

    /** How tall the line above must be, relative to this one, to count. */
    private const val MIN_TITLE_STACK_RATIO = 0.85f
}
