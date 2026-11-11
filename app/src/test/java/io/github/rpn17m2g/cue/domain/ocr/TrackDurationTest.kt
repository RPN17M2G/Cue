package io.github.rpn17m2g.cue.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackDurationTest {

    @Test
    fun `reads the total from a position and a duration on the player`() {
        val frame = frame(
            line("9:41", top = 0.01f),
            line("0:42", top = 0.75f),
            line("3:24", top = 0.75f),
        )

        assertEquals(204_000L, TrackDuration.fromFrameTexts(listOf(frame)))
    }

    @Test
    fun `ignores the status bar clock`() {
        val frame = frame(
            line("9:41", top = 0.01f),
            line("0:12", top = 0.75f),
            line("3:24", top = 0.75f),
        )

        assertEquals(204_000L, TrackDuration.fromFrameTexts(listOf(frame)))
    }

    @Test
    fun `a lone position under a minute is not mistaken for the track length`() {
        val frame = frame(line("0:42", top = 0.75f))

        assertNull(TrackDuration.fromFrameTexts(listOf(frame)))
    }

    @Test
    fun `a lone duration over a minute is accepted`() {
        val frame = frame(line("4:07", top = 0.75f))

        assertEquals(247_000L, TrackDuration.fromFrameTexts(listOf(frame)))
    }

    @Test
    fun `supports hour long tracks`() {
        val frame = frame(
            line("0:05", top = 0.75f),
            line("1:03:24", top = 0.75f),
        )

        assertEquals(3_804_000L, TrackDuration.fromFrameTexts(listOf(frame)))
    }

    @Test
    fun `takes the reading that repeats across frames`() {
        val frames = listOf(
            frame(line("0:30", top = 0.75f), line("3:24", top = 0.75f)),
            frame(line("0:31", top = 0.75f), line("3:24", top = 0.75f)),
            frame(line("0:32", top = 0.75f), line("3:24", top = 0.75f)),
            frame(line("4:10", top = 0.75f)),
        )

        assertEquals(204_000L, TrackDuration.fromFrameTexts(frames))
    }

    @Test
    fun `finds clocks embedded in a longer line`() {
        val frame = frame(line("0:42 / 3:24", top = 0.75f))

        assertEquals(204_000L, TrackDuration.fromFrameTexts(listOf(frame)))
    }

    @Test
    fun `rejects implausible values`() {
        val frame = frame(
            line("0:05", top = 0.75f),
            line("99:99", top = 0.75f),
        )

        assertNull(TrackDuration.fromFrameTexts(listOf(frame)))
    }

    private fun frame(vararg lines: OcrLine) = OcrFrameText(timestampMs = 0, lines = lines.toList())

    private fun line(text: String, top: Float) = OcrLine(
        text = text,
        box = OcrBox(left = 0.1f, top = top, right = 0.9f, bottom = top + 0.05f),
    )
}
