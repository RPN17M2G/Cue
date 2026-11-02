package io.github.rpn17m2g.cue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimClockTest {

    @Test
    fun `format uses minutes and seconds under an hour`() {
        assertEquals("0:00", TrimClock.format(0))
        assertEquals("0:12", TrimClock.format(12_400))
        assertEquals("3:41", TrimClock.format(221_000))
        assertEquals("59:59", TrimClock.format(3_599_000))
    }

    @Test
    fun `format grows to hours`() {
        assertEquals("1:00:00", TrimClock.format(3_600_000))
        assertEquals("1:02:03", TrimClock.format(3_723_000))
    }

    @Test
    fun `parse accepts the shapes the form produces`() {
        assertEquals(12_000L, TrimClock.parse("0:12"))
        assertEquals(221_000L, TrimClock.parse("3:41"))
        assertEquals(45_000L, TrimClock.parse("45"))
        assertEquals(3_723_000L, TrimClock.parse("1:02:03"))
        assertEquals(4_500_000L, TrimClock.parse("75:00"))
    }

    @Test
    fun `parse rejects malformed or out-of-range text`() {
        assertNull(TrimClock.parse(""))
        assertNull(TrimClock.parse("1:75"))
        assertNull(TrimClock.parse("1:5"))
        assertNull(TrimClock.parse("one:two"))
        assertNull(TrimClock.parse("1:2:3:4"))
        assertNull(TrimClock.parse("-1:00"))
    }

    @Test
    fun `format round-trips through parse`() {
        listOf(0L, 1_000L, 12_400L, 221_000L, 3_599_000L, 3_723_000L).forEach { ms ->
            assertEquals(ms / 1_000 * 1_000, TrimClock.parse(TrimClock.format(ms)))
        }
    }
}
