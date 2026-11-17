package io.github.rpn17m2g.cue.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `a reworded ad label still drops the ad card`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("PyCharm. Pytho..", 0.10f, 0.30f, 0.90f, 0.36f),
                line("Paid promotion", 0.10f, 0.37f, 0.30f, 0.395f),
                line("favorite crime", 0.35f, 0.70f, 0.65f, 0.735f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("favorite crime", candidates.first().title)
        assertTrue(candidates.none { it.title.contains("PyCharm") })
    }

    @Test
    fun `a promoted by label still drops the ad card`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("Acme Cloud", 0.10f, 0.30f, 0.90f, 0.36f),
                line("Promoted by Acme", 0.10f, 0.37f, 0.30f, 0.395f),
                line("favorite crime", 0.35f, 0.70f, 0.65f, 0.735f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("favorite crime", candidates.first().title)
        assertTrue(candidates.none { it.title.contains("Acme") })
    }

    @Test
    fun `a reworded Hebrew ad label still drops the ad card`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("מוצר ממומן", 0.10f, 0.30f, 0.90f, 0.36f),
                line("ממומן על ידי", 0.10f, 0.37f, 0.30f, 0.395f),
                line("תן", 0.35f, 0.70f, 0.65f, 0.735f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("תן", candidates.first().title)
        assertTrue(candidates.none { it.title.contains("מוצר") })
    }

    @Test
    fun `an artist that merely contains ad is not an ad label`() {
        val frames = List(3) { index ->
            frame(
                index,
                line("Some Song", 0.30f, 0.50f, 0.70f, 0.545f),
                line("Bad Guy", 0.34f, 0.56f, 0.66f, 0.587f),
            )
        }

        val candidates = OcrCandidateExtractor.extract(frames)

        assertTrue(candidates.any { it.title == "Some Song" })
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
    fun `overlapping marquee windows are stitched into one title`() {
        // A looping marquee never shows the whole title in one frame. Adjacent
        // windows share the words that just scrolled past, and the artist name
        // is printed on the cover art at the very top of the screen.
        val windows = listOf(
            "at I Like About You (feat. 1>",
            "out You (feat. Theresa Re >",
            "What I Like AboL >",
        )
        val frames = windows.mapIndexed { index, window ->
            frame(
                index,
                line("Elonas", 0.39f, 0.02f, 0.61f, 0.06f),
                line(window, 0.07f, 0.53f, 0.92f, 0.56f),
                line("Jonas Blue", 0.06f, 0.57f, 0.29f, 0.59f),
            )
        }

        val top = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Jonas Blue", top.artist)
        assertTrue(top.title, top.title.contains("Like About You", ignoreCase = true))
        assertFalse(top.title, top.title.contains("Elonas", ignoreCase = true))
    }

    @Test
    fun `a marquee is rebuilt from word adjacency rather than frame order`() {
        // The eight windows a real YouTube Music recording produced. Every one
        // is cut differently, and the first is a fragment of the title's
        // middle, so only how the words sit next to each other inside a window
        // can put the title back in order.
        val windows = listOf(
            "at I Like About You (feat. 1>",
            "ke About You (feat. There >",
            "out You (feat. Theresa Re >",
            "ou (feat. Theresa Rex)",
            "at. Theresa Rex)A REx",
            "eresa Rex) ERE SA What >",
            "a Rex)T THER What I Like >",
            "What I Like AboL >",
        )

        val top = OcrCandidateExtractor.extract(marqueeFrames(windows)).first()

        assertEquals("What I Like About You (feat. Theresa Rex)", top.title)
        assertEquals("Jonas Blue", top.artist)
    }

    @Test
    fun `a marquee that starts mid scroll is rebuilt in the same order`() {
        // A recording can begin with the title already half scrolled, so its
        // first frame is not the start of the title. Feeding the same windows
        // from a different point in the loop must not change the answer.
        val windows = listOf(
            "eresa Rex) ERE SA What >",
            "a Rex)T THER What I Like >",
            "What I Like AboL >",
            "at I Like About You (feat. 1>",
            "ke About You (feat. There >",
            "out You (feat. Theresa Re >",
            "ou (feat. Theresa Rex)",
            "at. Theresa Rex)A REx",
        )

        val top = OcrCandidateExtractor.extract(marqueeFrames(windows)).first()

        assertEquals("What I Like About You (feat. Theresa Rex)", top.title)
    }

    @Test
    fun `a marquee whose first word is pinned to the row edge is still completed`() {
        // From a clip cut to start mid-title. The title fits the row in three
        // windows, so "What" is the first word there and its link to "I" is
        // never interior; the pair repeats, which is what makes it trustworthy.
        val windows = listOf(
            "ou (feat. Theresa Rex)",
            "at. Theresa Rex)A REX",
            "1eresa Rex) RESA What",
            "THER VWhat I Like",
            "What I Like Abou )",
            "What I Like About You",
            "What I Like About You (fea",
            "at I Like About You (feat. 1",
        )

        val top = OcrCandidateExtractor.extract(marqueeFrames(windows)).first()

        assertEquals("What I Like About You (feat. Theresa Rex)", top.title)
        assertEquals("Jonas Blue", top.artist)
    }

    @Test
    fun `a clipped first word is not prepended to the title`() {
        // "at" is the tail of "What" that the marquee cut in half. It is the
        // first word of the same window in which "What" itself appears, so it
        // must not be added as the title's first word.
        val windows = listOf(
            "at I Like About You (feat. 1",
            "What I Like About You",
            "What I Like About You (feat.",
            "What I Like About You",
        )

        val top = OcrCandidateExtractor.extract(marqueeFrames(windows)).first()

        assertTrue(top.title, top.title.contains("What I Like About You", ignoreCase = true))
        assertFalse(top.title, top.title.startsWith("at ", ignoreCase = true))
    }

    @Test
    fun `a marquee cut mid feature drops the open bracket`() {
        val frames = listOf(
            frame(0, line("What I Like About You (feat. 1>", 0.07f, 0.53f, 0.92f, 0.56f)),
        )

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("What I Like About You", candidate.title)
    }

    @Test
    fun `a counter glued to a chrome label is dropped`() {
        val frames = listOf(
            frame(
                0,
                line("Get Lucky", 0.30f, 0.50f, 0.70f, 0.545f),
                line("99 Lyrics", 0.51f, 0.64f, 0.70f, 0.66f),
            ),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertTrue(candidates.none { it.title.contains("Lyrics", ignoreCase = true) })
        assertEquals("Get Lucky", candidates.first().title)
    }

    @Test
    fun `a real title that ends in a chrome word survives`() {
        val frames = listOf(frame(0, "Love Me Like"))

        assertTrue(OcrCandidateExtractor.extract(frames).any { it.title == "Love Me Like" })
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

    /**
     * Frames laid out like the YouTube Music player: the title scrolls in a
     * band above the artist, and the artist's name is also printed on the
     * cover art at the very top of the screen.
     */
    private fun marqueeFrames(windows: List<String>): List<OcrFrameText> =
        windows.mapIndexed { index, window ->
            frame(
                index * 2_000,
                line("Elonas", 0.39f, 0.02f, 0.61f, 0.06f),
                line(window, 0.07f, 0.53f, 0.92f, 0.56f),
                line("Jonas Blue", 0.06f, 0.57f, 0.29f, 0.59f),
            )
        }

    private fun line(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): OcrLine = OcrLine(text = text, box = OcrBox(left, top, right, bottom))
}

