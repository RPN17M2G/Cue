package io.github.rpn17m2g.cue.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataScorerTest {

    @Test
    fun `normalize ignores case diacritics and punctuation`() {
        assertEquals("beyonce halo", MetadataScorer.normalize("  Beyoncé, HALO! "))
        assertEquals("שלום עולם", MetadataScorer.normalize("שלום, עולם!"))
    }

    @Test
    fun `exact title and artist score one`() {
        val score = MetadataScorer.score(
            MetadataQuery(title = "Halo", artist = "Beyonce"),
            title = "halo",
            artist = "BEYONCÉ",
        )
        assertEquals(1.0, score, 0.0001)
    }

    @Test
    fun `title dominates when artist differs`() {
        val sameTitle = MetadataScorer.score(
            MetadataQuery(title = "Halo", artist = "Beyonce"),
            title = "Halo",
            artist = "Beyonce",
        )
        val differentArtist = MetadataScorer.score(
            MetadataQuery(title = "Halo", artist = "Beyonce"),
            title = "Halo",
            artist = "Somebody Else",
        )
        assertTrue(sameTitle > differentArtist)
        assertTrue(differentArtist >= 0.7)
    }

    @Test
    fun `contains match gives partial credit`() {
        val score = MetadataScorer.similarity("Halo", "Halo (Live)")
        assertTrue(score >= 0.8)
        assertTrue(score < 1.0)
    }

    @Test
    fun `year is extracted only from date-like strings`() {
        assertEquals("2015", "2015-10-23T07:00:00Z".toYear())
        assertEquals("1975", "1975".toYear())
        assertEquals(null, "Unknown".toYear())
        assertEquals(null, "123".toYear())
    }
}
