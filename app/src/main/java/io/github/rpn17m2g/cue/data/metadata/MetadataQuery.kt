package io.github.rpn17m2g.cue.data.metadata

/** The text Cue sends to an online provider. */
data class MetadataQuery(
    val title: String,
    val artist: String? = null,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }

    val searchTerm: String
        get() = listOfNotNull(
            title.trim().takeIf { it.isNotEmpty() },
            artist?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" ")
}

/** One cover picked from the artwork grid. */
data class ArtworkCandidate(
    val artworkUrl600: String,
    val album: String? = null,
    val artist: String? = null,
    val title: String? = null,
) {
    val artworkUrl: String get() = artworkUrl600
}

/** Chromaprint output plus the duration it was computed over. */
data class FingerprintInput(
    val fingerprint: String,
    val durationSeconds: Int,
)
