package io.github.rpn17m2g.cue.domain.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import io.github.rpn17m2g.cue.domain.model.TagDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarityTest {

    @Test
    fun `normalize removes case punctuation and diacritics`() {
        assertEquals("beyonce knowles", Similarity.normalize("  Beyoncé,  KNOWLES! "))
    }

    @Test
    fun `levenshtein distance counts edits`() {
        assertEquals(3, Similarity.levenshteinDistance("kitten", "sitting"))
    }

    @Test
    fun `levenshtein similarity is normalized to zero through one`() {
        assertEquals(0.0, Similarity.levenshteinSimilarity("", "song"), 0.0)
        assertEquals(1.0, Similarity.levenshteinSimilarity("Song!", "song"), 0.0)
        assertEquals(0.5, Similarity.levenshteinSimilarity("abcd", "abxy"), 0.0)
    }

    @Test
    fun `token overlap ignores word order`() {
        assertEquals(1.0, Similarity.tokenOverlap("Hello, World", "world hello"), 0.0)
        assertEquals(0.0, Similarity.tokenOverlap("Hello", "Goodbye"), 0.0)
    }

    @Test
    fun `matcher ranks the closest match first and ignores incoming score`() {
        val query = TagDraft(title = "Get Lucky", artist = "Daft Punk")
        val matches = listOf(
            MetadataMatch(
                title = "Get Lucky (Remix)",
                artist = "Daft Punk",
                source = IdentificationSource.MUSICBRAINZ,
                score = 0.99,
            ),
            MetadataMatch(
                title = "Get Lucky",
                artist = "Daft Punk",
                source = IdentificationSource.ITUNES,
                score = 0.01,
            ),
        )

        val ranked = MetadataMatcher.rank(query, matches)

        assertEquals("Get Lucky", ranked.first().title)
        assertEquals(IdentificationSource.ITUNES, ranked.first().source)
        assertTrue(ranked.first().score > ranked.last().score)
    }
}
