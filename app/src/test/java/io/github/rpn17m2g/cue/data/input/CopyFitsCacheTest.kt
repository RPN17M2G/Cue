package io.github.rpn17m2g.cue.data.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyFitsCacheTest {

    @Test
    fun `a document fits when the cache holds it plus the conversion headroom`() {
        val size = 3L * 1_024 * 1_024 * 1_024

        assertTrue(copyFitsCache(size, size + CACHE_HEADROOM_BYTES))
    }

    @Test
    fun `a document one byte over the headroom does not fit`() {
        val size = 3L * 1_024 * 1_024 * 1_024

        assertFalse(copyFitsCache(size, size + CACHE_HEADROOM_BYTES - 1))
    }

    @Test
    fun `an unknown size is attempted because there is nothing to compare`() {
        assertTrue(copyFitsCache(null, 0))
        assertTrue(copyFitsCache(-1, 0))
    }

    @Test
    fun `a cache smaller than the headroom cannot take any document`() {
        assertFalse(copyFitsCache(1, CACHE_HEADROOM_BYTES - 1))
    }

    @Test
    fun `an empty document still needs the headroom`() {
        assertTrue(copyFitsCache(0, CACHE_HEADROOM_BYTES))
        assertFalse(copyFitsCache(0, CACHE_HEADROOM_BYTES - 1))
    }
}
