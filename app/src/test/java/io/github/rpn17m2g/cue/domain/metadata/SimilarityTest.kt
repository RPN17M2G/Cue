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
    fun `normalize keeps hebrew letters and removes niqqud`() {
        assertEquals("שלום עולם", Similarity.normalize("שָׁלוֹם עוֹלָם"))
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
    fun `blank title query preserves provider score ordering`() {
        val query = TagDraft()
        val lowScore = MetadataMatch(
            title = "Alpha",
            artist = "Low",
            source = IdentificationSource.FINGERPRINT,
            score = 0.2,
        )
        val highScore = MetadataMatch(
            title = "Zulu",
            artist = "High",
            source = IdentificationSource.FINGERPRINT,
            score = 0.9,
        )

        val ranked = MetadataMatcher.rank(query, listOf(lowScore, highScore))

        assertEquals(listOf("Zulu", "Alpha"), ranked.map { it.title })
        assertEquals(0.9, ranked.first().score, 0.0)
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

    @Test
    fun `feature credit does not let a same-titled song outrank the right artist`() {
        val query = TagDraft(title = "By Your Side", artist = "Jonas Blue")
        val matches = listOf(
            MetadataMatch(
                title = "By Your Side",
                artist = "Sade",
                album = "Lovers Rock",
                source = IdentificationSource.ITUNES,
                score = 0.78,
            ),
            MetadataMatch(
                title = "By Your Side (feat. RAYE)",
                artist = "Jonas Blue",
                album = "By Your Side (feat. RAYE) - Single",
                source = IdentificationSource.ITUNES,
                score = 0.68,
            ),
        )

        val ranked = MetadataMatcher.rank(query, matches)

        assertEquals("By Your Side (feat. RAYE)", ranked.first().title)
        assertEquals("Jonas Blue", ranked.first().artist)
        assertEquals(1.0, ranked.first().score, 0.0)
    }

    @Test
    fun `version labels are ignored when comparing titles`() {
        assertEquals(1.0, Similarity.textSimilarity("Get Lucky", "Get Lucky - Radio Edit"), 0.0)
        assertEquals(1.0, Similarity.textSimilarity("Get Lucky", "Get Lucky (Remastered 2011)"), 0.0)
        assertEquals(1.0, Similarity.textSimilarity("Get Lucky", "Get Lucky (Album Version)"), 0.0)
    }

    @Test
    fun `a word that merely contains feat is not treated as a credit`() {
        // "defeat" must not collapse to "de", which would make it a perfect
        // match for any short query.
        assertTrue(Similarity.textSimilarity("Defeat", "De") < 1.0)
        assertEquals(1.0, Similarity.textSimilarity("Song feat Artist", "Song"), 0.0)
    }
}
