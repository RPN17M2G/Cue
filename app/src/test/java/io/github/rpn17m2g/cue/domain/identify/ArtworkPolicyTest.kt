package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The automatic cover is a convenience, not a decision the app may take away
 * from the user. These cases pin the boundary.
 */
class ArtworkPolicyTest {

    private val accepted = match(score = 0.9, artworkUrl = "https://cover/a.jpg")

    @Test
    fun `a confident match fills an empty cover`() {
        assertEquals(
            "https://cover/a.jpg",
            ArtworkPolicy.autoApplyUrl(
                match = accepted,
                hasArtwork = false,
                autoAppliedUrl = null,
                artworkEdited = false,
                networkEnabled = true,
            ),
        )
    }

    @Test
    fun `only the accepted tier is applied automatically`() {
        assertNull(policy(match(score = Confidence.ACCEPT_THRESHOLD - 0.01)))
        assertNull(policy(match(score = Confidence.CHECK_THRESHOLD - 0.01)))
    }

    @Test
    fun `a match without a cover is ignored`() {
        assertNull(policy(match(score = 0.9, artworkUrl = null)))
        assertNull(policy(match(score = 0.9, artworkUrl = "  ")))
    }

    @Test
    fun `network lookups off means no download`() {
        assertNull(policy(accepted, networkEnabled = false))
    }

    @Test
    fun `a cover the user touched is never replaced`() {
        assertNull(policy(accepted, artworkEdited = true))
    }

    @Test
    fun `a cover from the file or the user is kept`() {
        assertNull(policy(accepted, hasArtwork = true, autoAppliedUrl = null))
    }

    @Test
    fun `the same automatic cover is not downloaded twice`() {
        assertNull(
            policy(
                accepted,
                hasArtwork = true,
                autoAppliedUrl = "https://cover/a.jpg",
            ),
        )
    }

    @Test
    fun `a new match replaces the cover the app applied`() {
        assertEquals(
            "https://cover/a.jpg",
            policy(
                accepted,
                hasArtwork = true,
                autoAppliedUrl = "https://cover/old.jpg",
            ),
        )
    }

    private fun policy(
        match: MetadataMatch?,
        hasArtwork: Boolean = false,
        autoAppliedUrl: String? = null,
        artworkEdited: Boolean = false,
        networkEnabled: Boolean = true,
    ): String? = ArtworkPolicy.autoApplyUrl(
        match = match,
        hasArtwork = hasArtwork,
        autoAppliedUrl = autoAppliedUrl,
        artworkEdited = artworkEdited,
        networkEnabled = networkEnabled,
    )

    private fun match(score: Double, artworkUrl: String? = "https://cover/a.jpg") =
        MetadataMatch(
            title = "Get Lucky",
            artist = "Daft Punk",
            artworkUrl = artworkUrl,
            source = IdentificationSource.ITUNES,
            score = score,
        )
}
