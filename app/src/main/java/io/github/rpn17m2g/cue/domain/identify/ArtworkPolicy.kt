package io.github.rpn17m2g.cue.domain.identify

import io.github.rpn17m2g.cue.domain.model.MetadataMatch

/**
 * Decides whether the cover of a confident match may be downloaded into the
 * draft without asking. Kept out of the ViewModel so the rule can be tested
 * without Android.
 *
 * The cover is only ever filled in, never corrected behind the user's back: a
 * cover they picked, imported or removed wins, and an already-applied automatic
 * cover is only replaced when the match points somewhere else.
 */
object ArtworkPolicy {

    /**
     * The URL to download, or `null` when the draft must be left alone.
     *
     * @param autoAppliedUrl the URL this app last applied by itself, or `null`
     *   when the current cover came from the user, the file, or nowhere.
     */
    fun autoApplyUrl(
        match: MetadataMatch?,
        hasArtwork: Boolean,
        autoAppliedUrl: String?,
        artworkEdited: Boolean,
        networkEnabled: Boolean,
    ): String? {
        if (!networkEnabled || artworkEdited) return null
        if (match == null) return null
        if (Confidence.tier(match.score) != ConfidenceTier.ACCEPTED) return null
        val url = match.artworkUrl?.takeIf { it.isNotBlank() } ?: return null
        if (!hasArtwork) return url
        // Something already fills the cover. Replace it only when this app put
        // it there and the new match has a different image.
        return url.takeIf { autoAppliedUrl != null && autoAppliedUrl != url }
    }
}
