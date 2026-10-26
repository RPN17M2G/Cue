package io.github.rpn17m2g.cue.tags

import kotlin.math.max
import kotlin.math.roundToInt

data class IntSize(val width: Int, val height: Int)

/**
 * Bitmap-free sizing rules for cover art. Kept separate from [BitmapArtworkEncoder]
 * so the arithmetic can be tested without an Android runtime.
 */
object ArtworkScaling {

    fun fitInside(width: Int, height: Int, maxDimension: Int): IntSize {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(maxDimension > 0) { "maxDimension must be positive" }
        val scale = minOf(1.0, maxDimension.toDouble() / max(width, height))
        return IntSize(
            width = (width * scale).roundToInt().coerceAtLeast(1),
            height = (height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    /** Largest power-of-two subsample that leaves more pixels than the target. */
    fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(maxDimension > 0) { "maxDimension must be positive" }
        var sample = 1
        while (width / (sample * 2) >= maxDimension && height / (sample * 2) >= maxDimension) {
            sample *= 2
        }
        return sample
    }
}
