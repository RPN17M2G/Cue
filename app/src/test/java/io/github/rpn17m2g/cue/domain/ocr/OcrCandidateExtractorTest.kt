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

