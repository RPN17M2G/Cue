package io.github.rpn17m2g.cue.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricTimelineTest {

    @Test
    fun `parses the common lrc timestamp widths`() {
        val lrc = """
            [00:01]First
            [00:02.5]Second
            [00:03.25]Third
            [00:04.125]Fourth
            [01:05.07]Fifth
        """.trimIndent()

        assertEquals(
            listOf(
                LyricLine(1_000, "First"),
                LyricLine(2_500, "Second"),
                LyricLine(3_250, "Third"),
                LyricLine(4_125, "Fourth"),
                LyricLine(65_070, "Fifth"),
            ),
            parseLrc(lrc),
        )
    }

    @Test
    fun `accepts the colon fraction and several stamps on one line`() {
        val lrc = "[00:10:50][01:20.00]Chorus\n[00:05.00]Intro"

        assertEquals(
            listOf(
                LyricLine(5_000, "Intro"),
                LyricLine(10_500, "Chorus"),
                LyricLine(80_000, "Chorus"),
            ),
            parseLrc(lrc),
        )
    }

    @Test
    fun `ignores metadata untimed text and blank lines`() {
        val lrc = """
            [ar:Jonas Blue]
            [ti:By Your Side]
            [by:someone]
            an untimed line
            [00:12.00]Words

            [00:15.00]
        """.trimIndent()

        assertEquals(listOf(LyricLine(12_000, "Words")), parseLrc(lrc))
    }

    @Test
    fun `sorts lines that arrive out of order`() {
        assertEquals(
            listOf(LyricLine(1_000, "One"), LyricLine(2_000, "Two")),
            parseLrc("[00:02.00]Two\n[00:01.00]One"),
        )
    }

    @Test
    fun `an lrc without timestamps parses to nothing`() {
        assertTrue(parseLrc("Just words\n[not a stamp]").isEmpty())
        assertTrue(parseLrc("").isEmpty())
    }

    @Test
    fun `canonical lrc round trips through the parser`() {
        // LRC timestamps are centiseconds, so the canonical form is exact for
        // any time that is already a multiple of 10 ms.
        val lines = listOf(
            LyricLine(1_230, "First line"),
            LyricLine(65_070, "שלום"),
        )

        assertEquals(lines, parseLrc(lines.toLrc()))
        assertEquals("[00:01.23]First line\n[01:05.07]שלום", lines.toLrc())
    }

    @Test
    fun `re-timing a trimmed clip drops earlier lines and keeps the current one`() {
        val lines = listOf(
            LyricLine(1_000, "One"),
            LyricLine(10_000, "Two"),
            LyricLine(20_000, "Three"),
            LyricLine(30_000, "Four"),
        )

        assertEquals(
            listOf(
                LyricLine(0, "Two"),
                LyricLine(7_500, "Three"),
                LyricLine(17_500, "Four"),
            ),
            lines.shiftedTo(clipStartMs = 12_500),
        )
    }

    @Test
    fun `a line starting exactly at the cut replaces the clamped one`() {
        val lines = listOf(
            LyricLine(1_000, "One"),
            LyricLine(10_000, "Two"),
            LyricLine(20_000, "Three"),
        )

        // "One" would clamp to 0:00 as well, and only one line can own that
        // moment; the line that actually starts at the cut is the honest one.
        assertEquals(
            listOf(LyricLine(0, "Two"), LyricLine(10_000, "Three")),
            lines.shiftedTo(clipStartMs = 10_000),
        )
    }

    @Test
    fun `a clip starting inside a line keeps that line at zero`() {
        val lines = listOf(
            LyricLine(1_000, "One"),
            LyricLine(10_000, "Two"),
        )

        assertEquals(
            listOf(LyricLine(0, "One"), LyricLine(4_000, "Two")),
            lines.shiftedTo(clipStartMs = 6_000),
        )
    }

    @Test
    fun `re-timing clips lines after the end and leaves an untrimmed list alone`() {
        val lines = listOf(
            LyricLine(1_000, "One"),
            LyricLine(10_000, "Two"),
            LyricLine(20_000, "Three"),
        )

        assertEquals(lines, lines.shiftedTo(clipStartMs = 0))
        assertEquals(
            listOf(LyricLine(0, "One"), LyricLine(9_000, "Two")),
            lines.shiftedTo(clipStartMs = 1_000, clipEndMs = 15_000),
        )
    }
}
