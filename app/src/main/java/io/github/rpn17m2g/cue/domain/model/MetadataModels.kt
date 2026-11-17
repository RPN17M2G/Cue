package io.github.rpn17m2g.cue.domain.model

/** Where a piece of metadata came from, surfaced as a badge in the review UI. */
enum class IdentificationSource {
    EMBEDDED_TAGS,
    OCR_LATIN,
    OCR_HEBREW,
    ITUNES,
    MUSICBRAINZ,
    FINGERPRINT,
    LRCLIB,
    DEEZER,
    MANUAL,
}

/** A candidate song returned by a metadata provider or an audio recognizer. */
data class MetadataMatch(
    val title: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val artworkUrl: String? = null,
    val artwork: Artwork? = null,
    val source: IdentificationSource,
    val score: Double = 0.0,
    /** Standard codes and identifiers providers return alongside the names. */
    val isrc: String? = null,
    val label: String? = null,
    val bpm: Int? = null,
    val replayGainDb: Double? = null,
    val recordingMbid: String? = null,
    val releaseMbid: String? = null,
    val artistMbid: String? = null,
)

/** Input accepted by Cue, normalized away from Android types. */
sealed interface SongInput {
    val uri: String
    val displayName: String
    val mimeType: String?

    data class Video(
        override val uri: String,
        override val displayName: String,
        override val mimeType: String? = null,
        val durationMs: Long? = null,
    ) : SongInput

    data class Mp3(
        override val uri: String,
        override val displayName: String,
        override val mimeType: String? = "audio/mpeg",
    ) : SongInput
}

data class TrimRange(
    val startMs: Long,
    val endMs: Long,
) {
    init {
        require(startMs >= 0) { "startMs must be non-negative" }
        require(endMs > startMs) { "endMs must be greater than startMs" }
    }

    val durationMs: Long get() = endMs - startMs
}
