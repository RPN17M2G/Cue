package io.github.rpn17m2g.cue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `a typed start must leave a second before the end`() {
        assertTrue(TrimMath.acceptsStart(0, 60_000, 60_000))
        assertFalse(TrimMath.acceptsStart(59_500, 60_000, 60_000))
        assertFalse(TrimMath.acceptsStart(60_000, 60_000, 60_000))
    }

    @Test
    fun `a typed start cannot leave the track`() {
        assertFalse(TrimMath.acceptsStart(-1, 60_000, 60_000))
        assertFalse(TrimMath.acceptsStart(60_001, 60_000, 60_000))
    }

    @Test
    fun `a typed end must leave a second after the start`() {
        assertTrue(TrimMath.acceptsEnd(60_000, 0, 60_000))
        assertFalse(TrimMath.acceptsEnd(500, 0, 60_000))
        assertFalse(TrimMath.acceptsEnd(60_001, 0, 60_000))
    }

    @Test
    fun `effective duration prefers the trim window`() {
        assertEquals(10_000L, TrimMath.effectiveDurationMs(TrimRange(0, 10_000), 60_000))
        assertEquals(60_000L, TrimMath.effectiveDurationMs(null, 60_000))
        assertNull(TrimMath.effectiveDurationMs(null, null))
    }
}
