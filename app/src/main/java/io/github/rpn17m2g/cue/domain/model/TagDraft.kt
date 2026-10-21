package io.github.rpn17m2g.cue.domain.model

import kotlin.math.roundToLong

/**
 * Editable tag state shown on the review screen and written to the MP3.
 * Empty strings mean "field not set"; artwork is optional.
 */
data class TagDraft(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val artwork: Artwork? = null,
    val trackTotal: Int? = null,
) {
    val hasCoreTags: Boolean
        get() = title.isNotBlank() && artist.isNotBlank() && album.isNotBlank()
}

data class Artwork(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Artwork) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

/**
 * Estimated output size in bytes for an encoded MP3.
 * Duration and bitrate are combined directly; tag/artwork overhead is ignored
 * because it is negligible at the displayed precision.
 */
fun estimateMp3Bytes(durationMs: Long, preset: QualityPreset): Long {
    if (durationMs <= 0L) return 0L
    val bitsPerSecond = preset.approximateKbps * 1_000.0
    return ((durationMs / 1_000.0) * bitsPerSecond / 8.0).roundToLong()
}
