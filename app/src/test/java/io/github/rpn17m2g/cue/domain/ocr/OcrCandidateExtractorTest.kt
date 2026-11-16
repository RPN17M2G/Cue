package io.github.rpn17m2g.cue.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCandidateExtractorTest {

    @Test
    fun `stable title is preferred across frames`() {
        val frames = listOf(
            frame(0, "Song Title", "12:34", "Home"),
            frame(1, "Song Title", "Shuffle"),
            frame(2, "song   title"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Song Title", candidates.first().title)
        assertEquals(null, candidates.first().artist)
    }

    @Test
    fun `artist dash title is parsed`() {
        val frames = List(3) { index -> frame(index, "Daft Punk - Get Lucky") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
    }

    @Test
    fun `artist bullet title is parsed`() {
        val frames = List(2) { index -> frame(index, "Daft Punk \u2022 Get Lucky") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
    }

    @Test
    fun `artist is paired from the line directly under the title`() {
        val frames = List(4) { index ->
            frame(
                index,
                line("Get Lucky", 0.30f, 0.50f, 0.70f, 0.545f),
                line("Daft Punk", 0.34f, 0.56f, 0.66f, 0.587f),
                line("Random Access Memories", 0.30f, 0.60f, 0.70f, 0.621f),
            )
        }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
    }

    @Test
    fun `a line above the title is never its artist`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("Taylor Swift", 0.35f, 0.40f, 0.65f, 0.440f),
                line("Get Lucky", 0.30f, 0.50f, 0.70f, 0.545f),
                line("Imagine Dragons", 0.35f, 0.56f, 0.65f, 0.587f),
            )
        }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Imagine Dragons", candidate.artist)
    }

    @Test
    fun `a line that does not overlap the title horizontally is not its artist`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("Get Lucky", 0.30f, 0.50f, 0.70f, 0.545f),
                line("Taylor Swift", 0.80f, 0.56f, 0.97f, 0.585f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Get Lucky", candidates.first().title)
        assertEquals(null, candidates.first().artist)
    }

    @Test
    fun `a stacked title is preferred over the artist and album lines below it`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("פנתרה", 0.4017f, 0.54375f, 0.6000f, 0.56406f),
                line("נועהקירל", 0.4085f, 0.59375f, 0.5898f, 0.61406f),
                line("פנתרה", 0.4525f, 0.63672f, 0.5492f, 0.64688f),
            )
        }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("פנתרה", candidate.title)
        assertEquals("נועהקירל", candidate.artist)
    }

    @Test
    fun `status bar date and player label lose to the big centered title`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("9:41", 0.05f, 0.018f, 0.12f, 0.036f),
                line("28 Oct", 0.18f, 0.018f, 0.34f, 0.036f),
                line("Wi-Fi 82%", 0.72f, 0.018f, 0.94f, 0.036f),
                line("NOW PLAYING", 0.40f, 0.26f, 0.60f, 0.280f),
                line("Believer", 0.30f, 0.53f, 0.70f, 0.575f),
                line("Imagine Dragons", 0.35f, 0.59f, 0.65f, 0.617f),
                line("Evolve", 0.42f, 0.63f, 0.58f, 0.651f),
                line("0:42", 0.08f, 0.72f, 0.16f, 0.740f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Believer", candidates.first().title)
        assertEquals("Imagine Dragons", candidates.first().artist)
        assertTrue(candidates.none { it.title == "28 Oct" })
        assertTrue(candidates.none { it.title == "NOW PLAYING" })
        assertTrue(candidates.none { it.title == "Wi-Fi 82%" })
    }

    @Test
    fun `a transient notification loses to the steady title`() {
        val base = listOf(
            line("Believer", 0.30f, 0.53f, 0.70f, 0.575f),
            line("Imagine Dragons", 0.35f, 0.59f, 0.65f, 0.617f),
        )
        val frames = listOf(
            frame(0, *base.toTypedArray()),
            frame(1, *base.toTypedArray()),
            frame(
                2,
                line("Believer", 0.30f, 0.53f, 0.70f, 0.575f),
                line("New message from Dana", 0.20f, 0.74f, 0.80f, 0.785f),
            ),
            frame(3, *base.toTypedArray()),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Believer", candidates.first().title)
        assertEquals("Imagine Dragons", candidates.first().artist)
        val notification = candidates.firstOrNull { it.title == "New message from Dana" }
        assertTrue(
            notification == null || notification.score < candidates.first().score - 0.25,
        )
    }

    @Test
    fun `obvious ui noise is filtered out`() {
        val frames = listOf(
            frame(
                0,
                "12:34",
                "Battery 85%",
                "100%",
                "85 %",
                "9:41 AM",
                "12:34:56 PM",
                "01:02:03",
                "28 Oct",
                "28/10/2026",
                "Up next",
                "NOW PLAYING",
                "Shuffle",
                "Library",
                "Home",
                "Search",
                "Explore",
                "Sign in",
                "Add to playlist",
                "Play next",
                "Save to library",
                "Explicit",
                "https://example.com/watch?v=1",
                "www.example.com",
                "A",
                "x".repeat(121),
            ),
        )

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `Hebrew player chrome is filtered out`() {
        val frames = listOf(
            frame(
                0,
                "מתנגן כעת",
                "הבא בתור",
                "הוסף לפלייליסט",
                "שמור בספרייה",
                "הצג עוד",
                "התחברות",
                "ממומן",
                "שירים",
                "אלבומים",
                "וידאו רשמי",
            ),
        )

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `counters phone numbers and icon glued labels are chrome`() {
        val frames = listOf(
            frame(
                0,
                "2.6k",
                "14M",
                "| 50050760 > *",
                "USponsored",
                "> 4 080 *",
            ),
        )

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `a row chevron glued to the title is trimmed`() {
        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, "favorite crime >")))

        assertEquals("favorite crime", candidates.first().title)
    }

    @Test
    fun `an ad card does not beat the song title`() {
        // YouTube Music draws the product title at full size with "Sponsored"
        // underneath it, and the real player title smaller.
        val frames = List(3) { index ->
            frame(
                index,
                line("PyCharm. Pytho..", 0.10f, 0.30f, 0.90f, 0.36f),
                line("Sponsored", 0.10f, 0.37f, 0.30f, 0.395f),
                line("favorite crime", 0.35f, 0.70f, 0.65f, 0.735f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("favorite crime", candidates.first().title)
        assertTrue(candidates.none { it.title.contains("PyCharm") })
    }

    @Test
    fun `a title above an unrelated sponsored card survives`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("favorite crime", 0.05f, 0.88f, 0.45f, 0.91f),
                line("Olivia Rodrigo", 0.05f, 0.915f, 0.35f, 0.935f),
                line("PyCharm. Pytho..", 0.10f, 0.30f, 0.90f, 0.36f),
                line("Sponsored", 0.10f, 0.37f, 0.30f, 0.395f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertTrue(candidates.any { it.title == "favorite crime" })
        assertTrue(candidates.none { it.title.contains("PyCharm") })
    }

    @Test
    fun `a Hebrew title with a prefix letter is not treated as glued chrome`() {
        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, "הבית")))

        assertEquals("הבית", candidates.first().title)
    }

    @Test
    fun `leading punctuation is trimmed from a title`() {
        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, "<תן")))

        assertEquals("תן", candidates.first().title)
    }

    @Test
    fun `a short numeric title survives`() {
        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, "1999")))

        assertEquals("1999", candidates.first().title)
    }

    @Test
    fun `single word chrome is dropped when it is small but kept when it is the title`() {
        val chromeFrame = frame(
            0,
            line("Home", 0.06f, 0.92f, 0.18f, 0.945f),
            line("Get Lucky", 0.30f, 0.50f, 0.70f, 0.545f),
        )
        val titleFrame = frame(
            1,
            line("Home", 0.35f, 0.45f, 0.65f, 0.500f),
            line("Shuffle", 0.10f, 0.90f, 0.22f, 0.922f),
        )

        val candidates = OcrCandidateExtractor.extract(listOf(chromeFrame, titleFrame))

        assertTrue(candidates.none { it.title == "Shuffle" })
        assertTrue(candidates.any { it.title == "Get Lucky" })
        assertTrue(candidates.any { it.title == "Home" })
    }

    @Test
    fun `numeric and dotted titles are not mistaken for noise`() {
        val titles = listOf("1999", "Mr.Kitty", "A.B.C.")

        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, *titles.toTypedArray())))

        assertEquals(titles.toSet(), candidates.map { it.title }.toSet())
    }

    @Test
    fun `embedded urls and bare domains are filtered`() {
        val frames = listOf(
            frame(
                0,
                "music.youtube.com",
                "Watch on https://youtu.be/abc",
                "www.example.com",
            ),
        )

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `known tld domains are filtered anywhere`() {
        val frames = listOf(
            frame(
                0,
                "example.com",
                "youtu.be",
                "youtube.com/watch?v=abc",
                "open.spotify.com/track/xyz",
            ),
        )

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `bare clock title survives without status context`() {
        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, "4:33")))

        assertEquals(listOf("4:33"), candidates.map { it.title })
    }

    @Test
    fun `bare clock is filtered when the frame has a status line`() {
        val candidates = OcrCandidateExtractor.extract(listOf(frame(0, "4:33", "9:41 AM")))

        assertTrue(candidates.none { it.title == "4:33" })
    }

    @Test
    fun `a position and a total in the same frame are not a title`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("0:12", 0.05f, 0.84f, 0.16f, 0.87f),
                line("5:23", 0.80f, 0.84f, 0.95f, 0.87f),
            )
        }

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `a lone clock still survives when nothing else reads a time`() {
        val frames = List(3) { index ->
            frame(index, line("5:23", 0.30f, 0.45f, 0.70f, 0.52f))
        }

        assertEquals(listOf("5:23"), OcrCandidateExtractor.extract(frames).map { it.title })
    }

    @Test
    fun `meridiem and duration clocks are always filtered`() {
        assertTrue(OcrCandidateExtractor.extract(listOf(frame(0, "9:41 AM"))).isEmpty())
        assertTrue(OcrCandidateExtractor.extract(listOf(frame(0, "12:34:56 PM"))).isEmpty())
        assertTrue(OcrCandidateExtractor.extract(listOf(frame(0, "01:02:03"))).isEmpty())
    }

    @Test
    fun `symbol only lines are filtered out`() {
        val frames = listOf(frame(0, "\u2026", "\u25B8", "\u2014", "\u2026 \u2026", "\u2014\u2014"))

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `custom noise phrases are filtered`() {
        val frames = listOf(frame(0, "My Custom Junk", "Real Song"))

        val candidates = OcrCandidateExtractor.extract(
            frames,
            noisePhrases = setOf("my custom junk"),
        )

        assertEquals(listOf("Real Song"), candidates.map { it.title })
    }

    @Test
    fun `hebrew is preserved and scored like latin text`() {
        val frames = List(3) { index -> frame(index, "\u05e9\u05dc\u05d5\u05dd - \u05e2\u05d5\u05dc\u05dd") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("\u05e2\u05d5\u05dc\u05dd", candidate.title)
        assertEquals("\u05e9\u05dc\u05d5\u05dd", candidate.artist)
    }

    @Test
    fun `hebrew lines are skipped when hebrew support is disabled`() {
        val frames = listOf(frame(0, "\u05e9\u05dc\u05d5\u05dd - \u05e2\u05d5\u05dc\u05dd"))

        assertTrue(OcrCandidateExtractor.extract(frames, hebrewAware = false).isEmpty())
    }

    @Test
    fun `case and whitespace variants are aggregated`() {
        val frames = listOf(frame(0, "  Hello   World "), frame(1, "hello world"))

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals(1, candidates.size)
        assertEquals("Hello World", candidates.first().title)
    }

    @Test
    fun `equal scores are ordered deterministically by title`() {
        val frames = listOf(frame(0, "Beta"), frame(1, "Alpha"))

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals(listOf("Alpha", "Beta"), candidates.map { it.title })
    }

    @Test
    fun `status bar text never becomes a candidate`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("vIN 28", 0.35f, 0.004f, 0.52f, 0.018f),
                line("\u05e4\u05e0\u05ea\u05e8\u05d4", 0.35f, 0.48f, 0.65f, 0.530f),
                line("Noa Kirel", 0.38f, 0.545f, 0.62f, 0.575f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("\u05e4\u05e0\u05ea\u05e8\u05d4", candidates.first().title)
        assertTrue(candidates.none { it.title.contains("vIN") })
    }

    @Test
    fun `a title drawn at the top of the frame is kept`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("Get Lucky", 0.10f, 0.010f, 0.90f, 0.055f),
                line("Daft Punk", 0.20f, 0.065f, 0.80f, 0.100f),
            )
        }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
    }

    @Test
    fun `a scrolling title is stitched from its fragments`() {
        val frames = listOf(
            frame(0, "Get Luc"),
            frame(1, "et Lucky"),
            frame(2, "t Lucky"),
            frame(3, "Get Lucky"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals(listOf("Get Lucky"), candidates.map { it.title })
    }

    @Test
    fun `a title held on screen longer beats a title seen in more frames`() {
        val frames = listOf(
            frame(0, "Long Song"),
            frame(50, "Blip"),
            frame(51, "Blip"),
            frame(52, "Blip"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Long Song", candidates.first().title)
    }

    @Test
    fun `fragments are not stitched into a different title`() {
        val frames = listOf(
            frame(0, "Time"),
            frame(1, "Time After Time"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertTrue(candidates.any { it.title == "Time" })
        assertTrue(candidates.any { it.title == "Time After Time" })
    }

    @Test
    fun `a title with an artist is one sighting per frame`() {
        val frames = listOf(
            frame(
                0,
                line("Get Lucky", 0.30f, 0.50f, 0.70f, 0.545f),
                line("Daft Punk", 0.34f, 0.56f, 0.66f, 0.587f),
            ),
            frame(1, line("Something Else", 0.30f, 0.50f, 0.70f, 0.545f)),
        )

        val candidate = OcrCandidateExtractor.extract(frames)
            .first { it.title == "Get Lucky" }

        // One of two frames: 0.45 * 0.5 + 0.45 * 1.0 + 0.1. The paired and the
        // solo key for the same line must not each add the frame's time.
        assertEquals(0.775, candidate.score, 0.001)
    }

    @Test
    fun `a longer queue row does not swallow the stable title`() {
        val frames = listOf(
            frame(0, "Get Lucky"),
            frame(1, "Get Lucky"),
            frame(2, "Get Lucky"),
            frame(3, "Get Lucky Official"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Get Lucky", candidates.first().title)
    }

    @Test
    fun `equal timestamps fall back to how many frames saw the title`() {
        val frames = listOf(
            frame(0, "Once"),
            frame(0, "Twice"),
            frame(0, "Twice"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Twice", candidates.first().title)
    }

    @Test
    fun `a repeated timestamp does not add time to either frame`() {
        val frames = listOf(
            frame(0, "First"),
            frame(1, "Second"),
            frame(1, "Second"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        // The second frame stands for no interval, so the two titles tie on
        // stability and keep the deterministic order instead of one doubling.
        assertTrue(candidates.all { it.score <= 0.45 + 0.45 + 0.1 })
        assertEquals(2, candidates.size)
    }

    @Test
    fun `official video qualifiers are stripped from the title`() {
        val frames = List(3) { index -> frame(index, "Believer (Official Music Video)") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Believer", candidate.title)
    }

    @Test
    fun `a dash qualifier is stripped without eating the title`() {
        val frames = List(3) { index -> frame(index, "Believer - Official Audio") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Believer", candidate.title)
    }

    @Test
    fun `track number prefixes are stripped`() {
        val frames = List(3) { index -> frame(index, "01. Believer") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Believer", candidate.title)
    }

    @Test
    fun `a lyric caption is not offered as a title`() {
        val frames = List(3) { index ->
            frame(
                index,
                "Believer",
                "I was broken from a young age, taking my soul to the grave",
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Believer", candidates.first().title)
        assertTrue(candidates.none { it.title.contains("broken") })
    }

    @Test
    fun `view counts and progress lines are chrome`() {
        val frames = List(3) { index ->
            frame(index, "Believer", "1.2M views", "0:12 / 3:45")
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals(listOf("Believer"), candidates.map { it.title })
    }

    private fun frame(timestamp: Int, vararg lines: String): OcrFrameText =
        OcrFrameText(timestamp.toLong(), lines.map { OcrLine(it) })

    private fun frame(timestamp: Int, vararg lines: OcrLine): OcrFrameText =
        OcrFrameText(timestamp.toLong(), lines.toList())

    private fun line(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): OcrLine = OcrLine(text = text, box = OcrBox(left, top, right, bottom))
}

