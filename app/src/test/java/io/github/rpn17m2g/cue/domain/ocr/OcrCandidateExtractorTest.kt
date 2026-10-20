package io.github.rpn17m2g.cue.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrCandidateExtractorTest {

    @Test
    fun `stable title is preferred across frames`() {
        val frames = listOf(
            listOf("Song Title", "12:34", "Home"),
            listOf("Song Title", "Shuffle"),
            listOf("song   title"),
        )

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals("Song Title", candidates.first().title)
        assertEquals(null, candidates.first().artist)
        assertEquals(0.999, candidates.first().score, 0.0001)
    }

    @Test
    fun `artist dash title is parsed`() {
        val frames = List(3) { listOf("Daft Punk - Get Lucky") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
        assertEquals(1.0, candidate.score, 0.0001)
    }

    @Test
    fun `artist bullet title is parsed`() {
        val frames = List(2) { listOf("Daft Punk • Get Lucky") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
    }

    @Test
    fun `title followed by artist is parsed as the youtube music layout`() {
        val frames = List(4) { listOf("Get Lucky", "Daft Punk", "Random Access Memories") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("Get Lucky", candidate.title)
        assertEquals("Daft Punk", candidate.artist)
        assertEquals(1.0, candidate.score, 0.0001)
    }

    @Test
    fun `obvious ui noise is filtered out`() {
        val frames = listOf(
            listOf(
                "12:34",
                "Battery 85%",
                "Up next",
                "Shuffle",
                "Library",
                "Home",
                "Search",
                "Explore",
                "Sign in",
                "Add to playlist",
                "Share",
                "Download",
                "Like",
                "More",
                "Play next",
                "Save to library",
                "https://example.com/watch?v=1",
                "www.example.com",
                "A",
                "x".repeat(121),
            ),
        )

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `numeric and dotted titles are not mistaken for noise`() {
        val titles = listOf("1999", "100%", "Mr.Kitty", "A.B.C.")

        val candidates = OcrCandidateExtractor.extract(listOf(titles))

        assertEquals(titles.toSet(), candidates.map { it.title }.toSet())
    }

    @Test
    fun `symbol only lines are filtered out`() {
        val frames = listOf(listOf("…", "▸", "—", "… …", "▸ ▸", "——"))

        assertTrue(OcrCandidateExtractor.extract(frames).isEmpty())
    }

    @Test
    fun `custom noise phrases are filtered`() {
        val frames = listOf(listOf("My Custom Junk", "Real Song"))

        val candidates = OcrCandidateExtractor.extract(
            frames,
            noisePhrases = setOf("my custom junk"),
        )

        assertEquals(listOf("Real Song"), candidates.map { it.title })
    }

    @Test
    fun `hebrew is preserved and scored like latin text`() {
        val frames = List(3) { listOf("שלום - עולם") }

        val candidate = OcrCandidateExtractor.extract(frames).first()

        assertEquals("עולם", candidate.title)
        assertEquals("שלום", candidate.artist)
        assertEquals(1.0, candidate.score, 0.0001)
    }

    @Test
    fun `hebrew lines are skipped when hebrew support is disabled`() {
        val frames = listOf(listOf("שלום - עולם"))

        assertTrue(OcrCandidateExtractor.extract(frames, hebrewAware = false).isEmpty())
    }

    @Test
    fun `case and whitespace variants are aggregated`() {
        val frames = listOf(listOf("  Hello   World "), listOf("hello world"))

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals(1, candidates.size)
        assertEquals("Hello World", candidates.first().title)
        assertEquals(0.999, candidates.first().score, 0.0001)
    }

    @Test
    fun `equal scores are ordered deterministically by title`() {
        val frames = listOf(listOf("Beta"), listOf("Alpha"))

        val candidates = OcrCandidateExtractor.extract(frames)

        assertEquals(listOf("Alpha", "Beta"), candidates.map { it.title })
    }
}
