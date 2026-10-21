package io.github.rpn17m2g.cue.tags

/** Encoded cover art plus the MIME type that describes the actual bytes. */
data class EncodedArtwork(
    val bytes: ByteArray,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedArtwork) return false
        return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
}

/**
 * Resizes cover art for embedding.
 *
 * The Android implementation lands at integration time and uses
 * `BitmapFactory`/`Bitmap.compress`: apply EXIF orientation, scale inside
 * [maxDimension], flatten transparency onto white, and return JPEG bytes.
 * [EncodedArtwork.mimeType] must describe the returned bytes; implementations must
 * never label PNG bytes as `image/jpeg`.
 */
interface ArtworkEncoder {
    fun downscaleToJpeg(bytes: ByteArray, maxDimension: Int = 1000): EncodedArtwork
}
