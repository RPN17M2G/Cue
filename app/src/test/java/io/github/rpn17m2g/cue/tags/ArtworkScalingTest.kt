package io.github.rpn17m2g.cue.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkScalingTest {

    @Test
    fun `landscape art is limited by width`() {
        assertEquals(IntSize(1000, 500), ArtworkScaling.fitInside(2000, 1000, 1000))
    }

    @Test
    fun `portrait art is limited by height`() {
        assertEquals(IntSize(500, 1000), ArtworkScaling.fitInside(1000, 2000, 1000))
    }

    @Test
    fun `art already inside the bound is not enlarged`() {
        assertEquals(IntSize(320, 200), ArtworkScaling.fitInside(320, 200, 1000))
    }

    @Test
    fun `sample size halves until the result stays above the target`() {
        assertEquals(1, ArtworkScaling.sampleSize(800, 600, 1000))
        assertEquals(2, ArtworkScaling.sampleSize(3000, 2000, 1000))
        assertEquals(4, ArtworkScaling.sampleSize(9000, 6000, 1000))
    }
}
