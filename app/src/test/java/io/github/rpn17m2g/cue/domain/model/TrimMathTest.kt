package io.github.rpn17m2g.cue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimMathTest {

    @Test
    fun `a full window means no trim`() {
        assertNull(TrimMath.trimRange(0, 60_000, 60_000))
    }

    @Test
    fun `a partial window becomes a trim range`() {
        assertEquals(TrimRange(5_000, 15_000), TrimMath.trimRange(5_000, 15_000, 60_000))
    }

    @Test
    fun `a window shorter than one second is ignored`() {
        assertNull(TrimMath.trimRange(5_000, 5_500, 60_000))
    }

    @Test
    fun `windows beyond the track are clamped`() {
        assertEquals(TrimRange(50_000, 60_000), TrimMath.trimRange(50_000, 90_000, 60_000))
    }

    @Test
    fun `clamping keeps at least one second between the handles`() {
        val window = TrimMath.clampWindow(10_000, 10_200, 60_000)

        assertEquals(10_000L, window.first)
        assertEquals(11_000L, window.last)
    }

    @Test
    fun `clamping a start near the end pulls it back`() {
        val window = TrimMath.clampWindow(60_000, 60_000, 60_000)

        assertEquals(59_000L, window.first)
        assertEquals(60_000L, window.last)
    }

    @Test
    fun `effective duration prefers the trim window`() {
        assertEquals(10_000L, TrimMath.effectiveDurationMs(TrimRange(0, 10_000), 60_000))
        assertEquals(60_000L, TrimMath.effectiveDurationMs(null, 60_000))
        assertNull(TrimMath.effectiveDurationMs(null, null))
    }
}
