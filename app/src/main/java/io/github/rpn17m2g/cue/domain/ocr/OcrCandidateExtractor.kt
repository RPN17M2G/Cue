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
 * stitched into one candidate for the vote, and the title itself is rebuilt
 * from the adjacency of the words inside each window — never from frame order,
 * which cannot be trusted when a recording starts mid-scroll. Scores are
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

    /** A digits-only line longer than this is a number, not a song name. */
    private const val MAX_NUMERIC_TITLE_LENGTH = 4

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
        // The ad card's call to action, which sits right above the title and
        // is not part of the song.
        "Buy now",
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
        "New songs",
        "New releases",
        "New music",
        "Trending",
        "Charts",
        "Quick picks",
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
        // Video and streaming chrome.
        "Views",
        "Subscribers",
        "Premieres",
        "Trending",
        "Shorts",
        "Play all",
        "Mix",
        "Autoplay",
        "Save",
        "Report",
        "Not interested",
        "Don't recommend channel",
        "Show less",
        "Show more",
        "Comments",
        "Description",
        "Official Video",
        "Official Music Video",
        "Official Audio",
        "Lyric Video",
        // The same chrome in Hebrew. OCR returns these without niqqud, and
        // without them a Hebrew player's own labels compete with the title.
        // Single words are only dropped when they are not title-sized, exactly
        // like their English counterparts above.
        "מתנגן כעת",
        "מנגן כעת",
        "מתנגן עכשיו",
        "מנגן עכשיו",
        "הבא בתור",
        "הבא בהמשך",
        "ערבוב",
        "ערבוב אקראי",
        "חזרה",
        "חזור",
        "חזרה על הכל",
        "חזרה על שיר",
        "שלח",
        "שלח למכשיר",
        "טיימר שינה",
        "מילים",
        "תור",
        "מפורש",
        "הוסף לפלייליסט",
        "הוספה לפלייליסט",
        "נוסף לפלייליסט",
        "הוסף לתור",
        "נגן הבא",
        "שמור בספרייה",
        "שמירה לספרייה",
        "שמור",
        "אהבתי",
        "אהב",
        "שיתוף",
        "הורדה",
        "הורד",
        "עוד אפשרויות",
        "בית",
        "חיפוש",
        "גלה",
        "ספרייה",
        "הספרייה שלך",
        "עיון",
        "רדיו",
        "פודקאסטים",
        "סרטונים",
        "אלבומים",
        "אמנים",
        "שירים",
        "פלייליסטים",
        "נוגן לאחרונה",
        "נוצר עבורך",
        "מומלץ",
        "המשך לצפייה",
        "הפעלה אוטומטית",
        "פרקים",
        "תמלול",
        "הצג עוד",
        "הצג פחות",
        "הצג הכל",
        "התחברות",
        "הרשמה",
        "שדרוג",
        "פרימיום",
        "הירשם",
        "ממומן",
        "פרסומת",
        "התקן",
        "פתח",
        "סגור",
        "ביטול",
        "בוצע",
        "דלג",
        "הבא",
        "הקודם",
        "צפיות",
        "מנויים",
        "בכורה",
        "טרנדים",
        "שורטס",
        "הפעל הכל",
        "מיקס",
        "דווח",
        "לא מעניין",
        "אל תמליץ על הערוץ",
        "תגובות",
        "תיאור",
        "וידאו רשמי",
        "קליפ רשמי",
        "אודיו רשמי",
        "וידאו עם מילים",
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
    private val PROGRESS = Regex(
        "^\\d{1,2}:\\d{2}(?::\\d{2})?\\s*/\\s*\\d{1,2}:\\d{2}(?::\\d{2})?$",
    )
    private val VIEW_COUNT = Regex(
        "(?i)^[\\d.,]+\\s*[km]?\\s+(?:views?|likes?|subscribers?|comments?)$",
    )
    /** Track numbers some players prefix to a title ("01. ", "3 - "). */
    private val TRACK_PREFIX = Regex("^\\d{1,3}(?:[.)]\\s+|\\s*[-\\u2013\\u2014]\\s+)")
    /** Bracketed qualifiers that are not part of the song's name. */
    private val BRACKET_DECORATION = Regex(
        "(?i)\\s*[\\[(]\\s*(?:official\\s+)?(?:music\\s+)?(?:video|audio|lyric\\s+video|lyrics?|" +
            "visuali[sz]er|performance|live|remaster(?:ed)?(?:\\s+\\d{4})?|explicit|hd|4k|mv)" +
            "\\s*[\\])]\\s*$",
    )
    /** The same qualifiers written after a dash, never a bare one-word title. */
    private val TRAILING_DECORATION = Regex(
        "(?i)\\s*[-\\u2013\\u2014|]\\s*(?:official\\s+(?:music\\s+)?(?:video|audio)|" +
            "official\\s+lyric\\s+video|lyric\\s+video|lyrics|audio|visuali[sz]er|" +
            "remaster(?:ed)?(?:\\s+\\d{4})?|explicit|hd|4k|live|performance)\\s*$",
    )
    /** Unambiguous multi-word qualifiers that need no separator. */
    private val TRAILING_BARE = Regex(
        "(?i)\\s*(?:official\\s+(?:music\\s+)?(?:video|audio)|" +
            "official\\s+lyric\\s+video|lyric\\s+video)\\s*$",
    )
    private val HAS_LETTER_OR_DIGIT = Regex("[\\p{L}\\p{Nd}]")
    /** A row chevron or a bullet read as part of the title. */
    private val EDGE_DECORATION = charArrayOf('<', '>', '\u2039', '\u203A', '\u00AB', '\u00BB', '|', '\u2022', '\u00B7')
    private val HAS_LETTER = Regex("\\p{L}")
    /** A like/view counter with a compact suffix ("2.6k", "14M"). */
    private val COMPACT_COUNT = Regex("(?i)^\\d+(?:[.,]\\d+)?\\s*[km]$")
    private val HEBREW = Regex("[\\u0590-\\u05FF\\uFB1D-\\uFB4F]")
    private val SEPARATOR = Regex("\\s+[-\\u2013\\u2014\\u2022|]\\s+")
    /**
     * Word stems that mark an ad card. They sit where the artist normally goes,
     * so a line whose artist matches one is an advertisement, not a song.
     *
     * Matching is per word, not by exact phrase, because feeds reword the
     * label: "Sponsored", "Sponsored by X", "Paid promotion", "Promoted",
     * "Ad ·". Matching "ad" as a raw substring would drop a real artist like
     * "Bad Guy", so it is only accepted as a whole word. The longer English
     * stems take suffixes ("advert" → "advertisement"), and Hebrew stems match
     * anywhere in the word because Hebrew attaches prefixes ("הממומן").
     */
    private val AD_LABEL_WORDS: Set<String> = setOf("ad")
    private val AD_LABEL_STEMS: Set<String> = setOf("sponsor", "promot", "advert")
    private val HEBREW_AD_STEMS: Set<String> = setOf("ממומן", "ממומנת", "פרסומת", "קידום")
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{Nd}]+")
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
            val cleaned = frame.lines.map { clean(it.text).orEmpty() }
            val hasStatusClock = cleaned.any { text ->
                MERIDIEM.containsMatchIn(text) || DURATION.matches(text)
            }
            // A player draws the position and the total together ("0:12" on
            // the left, "5:23" on the right). Two clock readings in one frame
            // are that progress row, not a title that happens to look like a
            // time. Without this the total was read as the song's name and
            // "5:23" was searched for as a title.
            val clockReadings = cleaned.count { text ->
                BARE_CLOCK.matches(text) || DURATION.matches(text) || PROGRESS.matches(text)
            }
            val bareClockIsChrome = hasStatusClock || clockReadings >= 2
            val frameLargest = frame.lines
                .mapNotNull { it.box?.height }
                .filter { it > 0f }
                .maxOrNull() ?: 0f
            val kept = frame.lines
                .mapNotNull { line ->
                    keep(line, noiseKeys, hebrewAware, bareClockIsChrome, frameLargest)
                }
            if (kept.isEmpty()) return@forEachIndexed

            val weight = weights[frameIndex]
            val largest = kept.maxOf { it.prominence }
            val seen = HashSet<CandidateKey>()
            kept.forEachIndexed lines@{ index, line ->
                if (isAdTitle(line, frame.lines)) return@lines
                val quality = qualityOf(line, largest)
                val subtitle = looksLikeSubtitle(kept, index)
                val artist = line.inlineArtist ?: bestArtist(kept, index)?.text
                // An ad card puts "Sponsored" where the artist goes. The line
                // above it is the product being advertised ("PyCharm"), not a
                // song, and it is often the largest text in the frame, so it
                // would outrank the real title without this.
                if (artist != null && isAdLabel(artist)) return@lines
                if (artist != null) {
                    val pairKey = CandidateKey(normalize(line.text), normalize(artist))
                    if (seen.add(pairKey)) {
                        record(
                            stats,
                            pairKey,
                            line.text,
                            artist,
                            quality,
                            subtitle,
                            frameIndex,
                            weight,
                            line.box,
                        )
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
                        line.box,
                    )
                }
            }
        }

        val merged = mergeFragments(stats.values)
        return merged
            .map { stat ->
                val stability = (stat.weight / totalWeight).coerceIn(0.0, 1.0)
                val base = STABILITY_WEIGHT * stability + QUALITY_WEIGHT * stat.bestQuality
                val artistBonus = if (stat.artist != null) ARTIST_WEIGHT else 0.0
                stat to OcrCandidate(
                    // A looping marquee is rebuilt from word adjacency rather
                    // than frame order, so a recording that starts mid-scroll
                    // still yields the whole title in the right order.
                    // A marquee cut off mid-feature leaves an opening bracket
                    // with no closer; drop it from the final reading only, so
                    // it still helped the windows overlap while stitching.
                    title = stripUnbalancedTail(
                        reconstructMarquee(stat.title, marqueePool(stat, merged)) ?: stat.title,
                    ),
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
     * Containment needs at least one frame that saw the whole line. When a
     * marquee is never fully visible the reading stays a slice here, but the
     * title itself is later rebuilt from word adjacency; this merge only makes
     * sure the fragments' time is counted as one sighting for the vote.
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
                    title = mergeTitle(host.title, stat.title),
                    artist = host.artist ?: stat.artist,
                    frameWeights = unionWeights(host.frameWeights, stat.frameWeights),
                    bestQuality = maxOf(host.bestQuality, stat.bestQuality),
                    box = host.box ?: stat.box,
                    readings = unionReadings(host.readings, stat.readings),
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

    /** Every window read for the merged line, so the marquee can be rebuilt. */
    private fun unionReadings(first: List<String>, second: List<String>): List<String> {
        val union = LinkedHashSet<String>(first)
        union.addAll(second)
        return if (union.size <= MAX_MARQUEE_READINGS) {
            union.toList()
        } else {
            union.toList().takeLast(MAX_MARQUEE_READINGS)
        }
    }

    /**
     * Every word read on [anchor]'s text line, gathered transitively from the
     * candidates that share it.
     *
     * A marquee step can overlap its neighbour by a single word, which is not
     * enough to treat two candidates as the same sighting for ranking, but is
     * enough to know they are fragments of one scrolling line. Reconstruction
     * therefore pools the line while the vote stays per candidate. Candidates
     * on the same line that share no word at all — a different song after a
     * hard cut — stay out of the pool.
     */
    private fun marqueePool(anchor: CandidateStat, merged: List<CandidateStat>): List<String> {
        val group = ArrayList<CandidateStat>()
        group += anchor
        val remaining = merged.filter { it !== anchor }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                val related = group.any { member ->
                    sameLine(member.box, candidate.box) && shareAnyWord(member, candidate)
                }
                if (related) {
                    group += candidate
                    iterator.remove()
                    changed = true
                }
            }
        }
        val readings = LinkedHashSet<String>()
        group.forEach { readings.addAll(it.readings) }
        return readings.toList()
    }

    private fun shareAnyWord(first: CandidateStat, second: CandidateStat): Boolean {
        val firstWords = first.readings.flatMapTo(HashSet(), ::words)
        return second.readings.any { reading -> words(reading).any { it in firstWords } }
    }

    /**
     * Rebuilds a scrolling title from the windows OCR actually read.
     *
     * A marquee never shows the whole title in one frame, and the recording can
     * start with the title already half-way through its scroll, so frame order
     * cannot be trusted to say where the title begins. Word adjacency can: the
     * text inside a window is read left to right, so two words that sit next to
     * each other in a window really do follow one another. Only the first and
     * last word of a window are unreliable, because that is where the marquee
     * clips a word in half — so only bigrams *inside* a window are recorded.
     *
     * That gives a graph whose edges are trustworthy even when the start is
     * unknown: a title is the longest chain through it. Words must have been
     * read in at least [MIN_MARQUEE_SUPPORT] windows to be part of the
     * backbone, which drops one-off OCR garbage. When the marquee wrapped
     * around, several rotations have the same length; [hint] — the reading the
     * frame-order merge produced — breaks that tie, because it usually got the
     * order right even when it stitched in noise.
     *
     * Returns `null` when there is not enough evidence, so the caller keeps the
     * merged reading it already had.
     */
    private fun reconstructMarquee(hint: String, readings: List<String>): String? {
        val windows = readings
            .map(::marqueeTokens)
            .filter { it.size >= MIN_MARQUEE_WORDS }
            .distinctBy { window -> window.joinToString(" ") { it.key } }
        if (windows.size < MIN_MARQUEE_READINGS) return null

        val support = HashMap<String, Int>()
        val forms = HashMap<String, MutableMap<String, Int>>()
        val openers = HashSet<String>()
        val dotters = HashSet<String>()
        windows.forEach { window ->
            window.map { it.key }.toSet().forEach { key ->
                support[key] = (support[key] ?: 0) + 1
            }
            window.forEach { token ->
                val counts = forms.getOrPut(token.key) { LinkedHashMap() }
                counts[token.display] = (counts[token.display] ?: 0) + 1
                if (token.opensBracket) openers += token.key
                if (token.dotted) dotters += token.key
            }
        }

        val core = support.filterValues { it >= MIN_MARQUEE_SUPPORT }.keys.toSet()
        if (core.size < MIN_MARQUEE_WORDS || core.size > MAX_MARQUEE_NODES) return null

        // Only bigrams whose two words were both interior to a window. The
        // window's first and last word are exactly where the clip falls, so a
        // neighbour claimed from there may belong to a different part of the
        // title ("...Rex) | What I Like..." is not "Rex What").
        val edges = LinkedHashMap<String, MutableSet<String>>()
        windows.forEach { window ->
            for (i in 1..window.size - 3) {
                val from = window[i].key
                val to = window[i + 1].key
                if (from in core && to in core) {
                    edges.getOrPut(from) { LinkedHashSet() } += to
                }
            }
        }

        // A window that shows the whole title starts on its first word, so the
        // pair (first, second) is the only evidence for what precedes the
        // chain — and likewise (second-to-last, last) for how it ends. Footage
        // that starts mid-scroll routinely has a title word pinned to the left
        // edge in every window, so these pairs may extend the chain; they may
        // never choose its order. Both words must already be in the backbone,
        // and a head needs more than one sighting, because the first word of a
        // window is the one the marquee can clip ("at" from "What").
        val predecessors = HashMap<String, MutableMap<String, Int>>()
        val successors = HashMap<String, MutableMap<String, Int>>()
        windows.forEach { window ->
            val first = window.first().key
            val second = window[1].key
            if (first in core && second in core) bump(predecessors, second, first)
            val last = window.last().key
            val beforeLast = window[window.size - 2].key
            if (beforeLast in core && last in core) bump(successors, beforeLast, last)
        }

        val visited = HashSet<String>()
        val path = ArrayList<String>()
        var best: List<String> = emptyList()
        var bestRun = -1
        var steps = 0

        fun consider() {
            if (path.size < best.size) return
            val candidate = path.toList()
            val text = candidate.joinToString(" ")
            val run = sharedWordRun(text, hint)
            val supportSum = candidate.sumOf { support[it] ?: 0 }
            val better = when {
                candidate.size > best.size -> true
                candidate.size < best.size -> false
                run != bestRun -> run > bestRun
                else -> supportSum > best.sumOf { support[it] ?: 0 }
            }
            if (better) {
                best = candidate
                bestRun = run
            }
        }

        fun walk(node: String) {
            if (steps++ > MAX_MARQUEE_STEPS) return
            visited += node
            path += node
            consider()
            edges[node]?.sorted()?.forEach { next -> if (next !in visited) walk(next) }
            path.removeAt(path.lastIndex)
            visited -= node
        }

        core.sorted().forEach { node -> walk(node) }

        // Append the title's last word when a window read it together with the
        // word before it and it was seen often enough to be a word, not a clip.
        val chain = ArrayList(best)
        while (true) {
            val last = chain.lastOrNull() ?: break
            val next = extend(
                candidates = successors[last],
                chain = chain,
                minSeen = 1,
                clipped = { word -> isClippedTail(word, core) },
            ) ?: break
            chain += next
        }
        // Prepend the title's first word the same way. A word that is merely
        // the tail of a longer one ("at" from "What") is a clip, not a word.
        while (true) {
            val first = chain.firstOrNull() ?: break
            val previous = extend(
                candidates = predecessors[first],
                chain = chain,
                minSeen = MIN_MARQUEE_SUPPORT,
                clipped = { word -> isClippedHead(word, core) },
            ) ?: break
            chain.add(0, previous)
        }

        // A word glued to the closing paren of the previous one ("Rex)A") is a
        // fragment boundary, not part of the title.
        val trimmed = chain.dropLastWhile { it.length == 1 && !it[0].isDigit() }
        if (trimmed.size < MIN_MARQUEE_WORDS) return null

        val text = trimmed.joinToString(" ") { key ->
            // Most-read spelling wins; on a tie the quieter one does, because
            // OCR shouting ("REx") is usually noise on a correctly read word.
            val spelling = forms[key]?.maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenBy { entry -> -entry.key.count { it.isUpperCase() } }
                    .thenBy { entry -> -entry.key.length }
                    .thenBy { it.key },
            )?.key ?: key
            buildString {
                // The window opened a bracket on this word ("(feat."); the
                // closer is restored after the chain, not guessed in the middle.
                if (key in openers) append('(')
                append(spelling)
                if (key in dotters) append('.')
            }
        }
        return balanceTail(text).ifBlank { null }
    }

    /**
     * One word of a marquee window. [key] is what the graph compares, so case
     * and edge punctuation cannot split one word into two nodes; [display] is
     * the word itself, with its inner apostrophes and hyphens kept so "Don't"
     * and "Spider-Man" survive the rebuild.
     */
    private data class MarqueeToken(
        val key: String,
        val display: String,
        val opensBracket: Boolean,
        val dotted: Boolean,
    )

    /** Leading word characters, allowing an inner apostrophe or hyphen. */
    private val WORD_CORE = Regex(
        "^[^\\p{L}\\p{Nd}]*([\\p{L}\\p{Nd}]+(?:['\u2019-][\\p{L}\\p{Nd}]+)*)",
    )

    private fun marqueeTokens(reading: String): List<MarqueeToken> =
        reading.split(WHITESPACE)
            .filter { it.isNotEmpty() }
            .mapNotNull { raw ->
                val match = WORD_CORE.find(raw) ?: return@mapNotNull null
                val core = match.groupValues[1]
                MarqueeToken(
                    key = core.lowercase(Locale.ROOT),
                    display = core,
                    // "(feat." carries its opening bracket; it belongs to the
                    // title, while "Rex)" often loses its closer to the clip.
                    opensBracket = raw.takeWhile { !it.isLetterOrDigit() }.contains('('),
                    dotted = raw.getOrNull(match.range.last + 1) == '.',
                )
            }

    private fun bump(
        edges: MutableMap<String, MutableMap<String, Int>>,
        from: String,
        to: String,
    ) {
        val targets = edges.getOrPut(from) { HashMap() }
        targets[to] = (targets[to] ?: 0) + 1
    }

    /**
     * The word to add at one end of the chain, or `null` when nothing was seen
     * often enough to be more than clipping noise.
     */
    private fun extend(
        candidates: Map<String, Int>?,
        chain: List<String>,
        minSeen: Int,
        clipped: (String) -> Boolean,
    ): String? = candidates
        ?.filter { (word, seen) ->
            seen >= minSeen && word !in chain && !clipped(word)
        }
        ?.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key },
        )
        ?.key

    /** A clipped head is the tail of a longer word in the title ("at" → "What"). */
    private fun isClippedHead(word: String, core: Set<String>): Boolean =
        core.any { other -> other.length > word.length && other.endsWith(word) }

    /** A clipped tail is the start of a longer word in the title ("Abo" → "About"). */
    private fun isClippedTail(word: String, core: Set<String>): Boolean =
        core.any { other -> other.length > word.length && other.startsWith(word) }

    /**
     * Closes a bracket the marquee was cut inside of, and drops a stray closer
     * whose opener never made it into the rebuilt title.
     */
    private fun balanceTail(text: String): String {        var depth = 0
        text.forEach { char ->
            if (char == '(') depth++
            if (char == ')') depth--
        }
        if (depth <= 0) {
            if (!text.contains('(')) {
                return text.trimEnd(')', ' ')
            }
            return text
        }
        return text + ")".repeat(depth)
    }

    private fun canMerge(host: CandidateStat, fragment: CandidateStat): Boolean {
        val hostTitle = titleKey(host.title)
        val fragmentTitle = titleKey(fragment.title)
        if (fragmentTitle.length < MIN_FRAGMENT_LENGTH) return false
        val hostArtist = host.artist?.let(::normalize)
        val fragmentArtist = fragment.artist?.let(::normalize)
        if (hostArtist != null && fragmentArtist != null && hostArtist != fragmentArtist) return false

        val shorter = minOf(hostTitle.length, fragmentTitle.length)
        val longer = maxOf(hostTitle.length, fragmentTitle.length)
        if (longer > 0 &&
            shorter.toDouble() / longer >= MIN_FRAGMENT_RATIO &&
            (hostTitle.contains(fragmentTitle) || fragmentTitle.contains(hostTitle))
        ) {
            return true
        }

        // Two windows of the same scrolling line. A marquee never shows the
        // whole title, so no window contains another; instead they share the
        // run of words that scrolled past ("I Like Abo" / "I Like About You").
        // Merging them restores the frame support the title actually earned.
        // Only fragments on the same line qualify, so a lyric drawn just above
        // the title is not swallowed into it.
        if (!sameLine(host.box, fragment.box)) return false
        return sharedWordRun(host.title, fragment.title) >= MIN_MERGE_WORDS
    }

    /**
     * True when two fragments occupy the same text line. Marquee windows keep
     * the line's vertical band and only move horizontally; a stacked artist or
     * lyric line sits in a different band.
     */
    private fun sameLine(first: OcrBox?, second: OcrBox?): Boolean {
        if (first == null || second == null) return false
        val overlap = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
        val shorter = minOf(first.height, second.height)
        return shorter > 0f && overlap / shorter >= MIN_SAME_LINE_OVERLAP
    }

    /** Longest run of whole words the two readings have in common. */
    private fun sharedWordRun(first: String, second: String): Int {
        val a = words(first)
        val b = words(second)
        var best = 0
        for (i in a.indices) {
            for (j in b.indices) {
                var run = 0
                while (i + run < a.size && j + run < b.size && a[i + run] == b[j + run]) run++
                if (run > best) best = run
            }
        }
        return best
    }

    private fun words(value: String): List<String> =
        value.split(WHITESPACE)
            .map { word -> word.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

    /**
     * Joins two readings of the same scrolling line. When one window already
     * contains the other, the longer reading wins. Otherwise the longest run of
     * shared words anchors the union, and the longer of the two prefixes and
     * suffixes is kept so a truncated word ("Abo") loses to the full one
     * ("About"). Only the shared run is ever written once, so the merge cannot
     * duplicate the scrolled text.
     */
    private fun mergeTitle(first: String, second: String): String {
        val a = first.split(WHITESPACE).filter { it.isNotEmpty() }
        val b = second.split(WHITESPACE).filter { it.isNotEmpty() }
        if (a.isEmpty()) return second
        if (b.isEmpty()) return first
        val na = a.map(::wordKey)
        val nb = b.map(::wordKey)

        // A window that merely contains the host is a queue row or a longer
        // variant ("Get Lucky Official"), not a better reading of the title.
        // Keep the host, exactly as containment-only stitching did.
        if (containsRun(na, nb) || containsRun(nb, na)) return first

        var bestI = 0
        var bestJ = 0
        var best = 0
        for (i in na.indices) {
            for (j in nb.indices) {
                var run = 0
                while (i + run < na.size &&
                    j + run < nb.size &&
                    na[i + run].isNotEmpty() &&
                    na[i + run] == nb[j + run]
                ) {
                    run++
                }
                if (run > best) {
                    best = run
                    bestI = i
                    bestJ = j
                }
            }
        }
        if (best == 0) return if (first.length >= second.length) first else second

        val prefix = if (runLength(a, 0, bestI) >= runLength(b, 0, bestJ)) {
            a.subList(0, bestI)
        } else {
            b.subList(0, bestJ)
        }
        val middle = a.subList(bestI, bestI + best)
        val suffix = if (runLength(a, bestI + best, a.size) >= runLength(b, bestJ + best, b.size)) {
            a.subList(bestI + best, a.size)
        } else {
            b.subList(bestJ + best, b.size)
        }
        return (prefix + middle + suffix).joinToString(" ").trim()
    }

    private fun wordKey(word: String): String =
        word.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun runLength(words: List<String>, from: Int, to: Int): Int =
        words.subList(from, to).sumOf { it.length }

    private fun containsRun(haystack: List<String>, needle: List<String>): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        for (start in 0..haystack.size - needle.size) {
            val window = haystack.subList(start, start + needle.size)
            if (window.indices.all { window[it].isNotEmpty() && window[it] == needle[it] }) return true
        }
        return false
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
        box: OcrBox?,
    ) {
        val existing = stats[key]
        stats[key] = existing?.copy(
            frameWeights = existing.frameWeights + (frameIndex to weight),
            bestQuality = maxOf(existing.bestQuality, quality),
            alwaysSubtitle = existing.alwaysSubtitle && subtitle,
            box = existing.box ?: box,
            readings = withReading(existing.readings, title),
        ) ?: CandidateStat(
            title = title,
            artist = artist,
            frameWeights = mapOf(frameIndex to weight),
            bestQuality = quality,
            alwaysSubtitle = subtitle,
            box = box,
            readings = listOf(title),
        )
    }

    /** Keeps every distinct way this line was read, newest last, bounded. */
    private fun withReading(existing: List<String>, reading: String): List<String> {
        if (reading in existing) return existing
        val next = existing + reading
        return if (next.size <= MAX_MARQUEE_READINGS) next else next.takeLast(MAX_MARQUEE_READINGS)
    }

    /** Drops chrome and keeps the geometry the ranking needs. */
    private fun keep(
        line: OcrLine,
        noiseKeys: Set<String>,
        hebrewAware: Boolean,
        bareClockIsChrome: Boolean,
        frameLargest: Float,
    ): KeptLine? {
        // Only the leading punctuation is decoration ("<תן" from a Hebrew
        // player's play glyph). A trailing "%" or ")" is meaningful: the chrome
        // patterns and the bracket stripper need the line intact.
        val raw = clean(line.text)?.trimStart { !it.isLetterOrDigit() } ?: return null
        if (raw.length > MAX_LINE_LENGTH) return null
        if (!HAS_LETTER_OR_DIGIT.containsMatchIn(raw)) return null
        // A phone number or a progress readout is not a title. Short numeric
        // names ("1999") are still allowed; long digit strings are chrome.
        if (!HAS_LETTER.containsMatchIn(raw) && (raw.length > MAX_NUMERIC_TITLE_LENGTH || raw.contains(' '))) {
            return null
        }
        if (!hebrewAware && HEBREW.containsMatchIn(raw)) return null

        val box = line.box
        val prominence = when {
            box == null -> UNKNOWN_PROMINENCE
            box.height <= 0f -> UNKNOWN_PROMINENCE
            else -> box.height
        }
        if (isStatusBarLine(box, prominence, frameLargest)) return null

        // Track numbers and "Official Video"-style qualifiers are packaging,
        // not the song's name. Removing them before scoring also lets a line
        // match its cleaner reading from another frame, so the vote adds up
        // instead of splitting into two candidates.
        val text = stripDecorations(stripTrackPrefix(raw))
        if (text.length < 2) return null
        if (isChrome(text, noiseKeys, bareClockIsChrome, prominence, frameLargest)) return null
        // The ad label is not a song either: without this, "Promoted by Acme"
        // becomes a candidate in its own right and can outrank the real title.
        if (isAdLabel(text)) return null
        if (isLikelyLyric(text)) return null

        val inline = splitInline(text)
        return KeptLine(
            text = inline?.title ?: text,
            inlineArtist = inline?.artist,
            box = box,
            prominence = prominence,
        )
    }

    private fun stripTrackPrefix(text: String): String =
        text.replaceFirst(TRACK_PREFIX, "").trim()

    private fun stripDecorations(text: String): String {
        var current = text.trim().trim(*EDGE_DECORATION)
        repeat(2) {
            val next = current
                .replace(BRACKET_DECORATION, "")
                .replace(TRAILING_DECORATION, "")
                .replace(TRAILING_BARE, "")
                .trim()
            if (next == current) return current
            current = next
        }
        return current
    }

    /**
     * A marquee cut off mid-feature leaves an opening bracket with no closer
     * ("What I Like About You (feat. 1"). Searching for the leftover would fail,
     * so the title before it is kept.
     */
    private fun stripUnbalancedTail(text: String): String {
        val open = text.indexOf('(')
        if (open <= 0) return text
        if (text.lastIndexOf(')') > open) return text
        return text.substring(0, open).trim().ifEmpty { text }
    }

    /**
     * Lyrics, captions and comment previews are long sentences. A song title is
     * rarely more than a dozen words, and an eight-word line that starts in
     * lower case and ends with sentence punctuation is prose, not a name.
     */
    private fun isLikelyLyric(text: String): Boolean {
        val words = text.count { it == ' ' } + 1
        if (words >= 14) return true
        return words >= 10 && text.any { it == ',' || it == '.' || it == '?' || it == '!' }
    }

    private fun isChrome(
        text: String,
        noiseKeys: Set<String>,
        bareClockIsChrome: Boolean,
        prominence: Float,
        frameLargest: Float,
    ): Boolean {
        val normalized = normalize(text)
        if (normalized in noiseKeys && !isTitleSized(normalized, prominence, frameLargest)) {
            return true
        }
        // An icon or badge glued to a label: ML Kit reads the speaker glyph
        // before "Sponsored" as a letter ("USponsored"). One leading character
        // over a known phrase is that artefact, not a title. Hebrew words take
        // one-letter prefixes as grammar, so a Hebrew title is never treated
        // this way: "הבית" is not "בית" (Home) with a stray glyph.
        if (normalized.length > 3 &&
            !HEBREW.containsMatchIn(normalized) &&
            normalized.drop(1) in noiseKeys
        ) {
            return true
        }
        if (COMPACT_COUNT.matches(text)) return true
        // A counter or badge glued to a one-word label is a button, not a title:
        // YouTube Music's lyrics button is read as "99 Lyrics" when its icon is
        // taken for digits. The label alone is already chrome; a number in front
        // must not turn it into a song.
        val labelWords = normalized.split(' ')
        if (labelWords.size > 1 &&
            labelWords.last() in noiseKeys &&
            labelWords.dropLast(1).none { word -> word.any { it.isLetter() } }
        ) {
            return true
        }
        if (MERIDIEM.containsMatchIn(text)) return true
        if (DURATION.matches(text)) return true
        if (PROGRESS.matches(text)) return true
        if (bareClockIsChrome && BARE_CLOCK.matches(text)) return true
        if (PERCENT_ANY.containsMatchIn(text)) return true
        if (VIEW_COUNT.matches(text)) return true
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
        // A real title drawn at the very top still fills the row, so it is wide.
        // The artist printed on the cover art is a short label ("Elonas" for
        // "Jonas Blue"), and cover art scrolled under the status bar is exactly
        // where that lands. Width tells the two apart without losing the title.
        if (box.width < STATUS_BAR_MAX_WIDTH) return true
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

    /**
     * A line whose nearest line below is an ad label is the advertised product,
     * not the song. The label itself is small and is normally dropped as chrome,
     * so the geometry is checked against the raw lines. Only the nearest line
     * counts: a "Sponsored" card elsewhere in the feed must not delete the
     * player's real title.
     */
    private fun isAdTitle(line: KeptLine, rawLines: List<OcrLine>): Boolean {
        val box = line.box ?: return false
        val nearest = rawLines
            .mapNotNull { raw ->
                val other = raw.box ?: return@mapNotNull null
                val gap = other.top - box.bottom
                if (gap < 0f || gap > box.height * MAX_ARTIST_GAP_IN_HEIGHTS) {
                    return@mapNotNull null
                }
                if (box.horizontalOverlap(other) < MIN_HORIZONTAL_OVERLAP) {
                    return@mapNotNull null
                }
                gap to normalize(clean(raw.text).orEmpty())
            }
            .minByOrNull { it.first }
        return nearest?.second?.let(::isAdLabel) == true
    }

    /**
     * True when [value] names an ad. Works on whole words so a real artist
     * that merely contains the letters of a stem ("Bad Guy") is not treated as
     * an advertisement.
     */
    private fun isAdLabel(value: String): Boolean {
        val words = normalize(value).split(WORD_SPLIT).filter { it.isNotEmpty() }
        return words.any { word ->
            // A bare "Ad" is a label; "Ad Astra" is a title, so the one-letter
            // stem only counts when it is the whole line.
            (words.size == 1 && word in AD_LABEL_WORDS) ||
                AD_LABEL_STEMS.any(word::startsWith) ||
                HEBREW_AD_STEMS.any(word::contains)
        }
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
        /** Where the line sat, so marquee windows can be told from stacked lines. */
        val box: OcrBox?,
        /** Every distinct window read for this line, kept for marquee rebuilding. */
        val readings: List<String>,
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

    /**
     * Two marquee windows must share at least this many whole words before they
     * are treated as the same line. One shared word ("You") is not enough.
     */
    private const val MIN_MERGE_WORDS = 2

    /** Two fragments are on the same line when their boxes overlap this much vertically. */
    private const val MIN_SAME_LINE_OVERLAP = 0.5f

    /**
     * A scrolling title can only be rebuilt from several different windows; one
     * or two readings are not enough to know which words are neighbours.
     */
    private const val MIN_MARQUEE_READINGS = 3

    /** A word must be read in this many windows to join the marquee backbone. */
    private const val MIN_MARQUEE_SUPPORT = 2

    /** A rebuilt title needs at least this many words to be trusted. */
    private const val MIN_MARQUEE_WORDS = 3

    /** A title is a line of text, so a graph bigger than this is not one. */
    private const val MAX_MARQUEE_NODES = 24

    /** Bound the chain search so a dense graph cannot stall identification. */
    private const val MAX_MARQUEE_STEPS = 20_000

    /** Distinct windows remembered per line, newest kept. */
    private const val MAX_MARQUEE_READINGS = 12

    /** Single-word chrome is dropped unless it is at least this big. */
    private const val SINGLE_WORD_TITLE_RATIO = 0.6f

    /** Lines that end above this fraction of the frame are in the status bar. */
    private const val STATUS_BAR_BAND = 0.06f

    /** A status-bar line is never the big text of its frame. */
    private const val STATUS_BAR_MAX_PROMINENCE = 0.45f

    /** A top-band line narrower than this is a label, not a title. */
    private const val STATUS_BAR_MAX_WIDTH = 0.45f

    /** Scores are compared at this precision, so near-ties fall to the layout. */
    private const val SCORE_TIE_PRECISION = 1000.0

    /** How tall the line above must be, relative to this one, to count. */
    private const val MIN_TITLE_STACK_RATIO = 0.85f
}
