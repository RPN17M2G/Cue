package io.github.rpn17m2g.cue.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSamplerTest {

    @Test
    fun `samples the scan window at the configured interval`() {
        val timestamps = FrameSampler.timestamps(
            durationMs = 60_000L,
            scanWindowSeconds = 15,
            intervalMs = 2_000L,
        )

        assertEquals(
            listOf(0L, 2_000L, 4_000L, 6_000L, 8_000L, 10_000L, 12_000L, 14_000L),
            timestamps,
        )
    }

    @Test
    fun `stops at the end of a short recording`() {
        val timestamps = FrameSampler.timestamps(
            durationMs = 3_500L,
            scanWindowSeconds = 15,
            intervalMs = 2_000L,
        )

        assertEquals(listOf(0L, 2_000L), timestamps)
    }

    @Test
    fun `clamps the scan window to the supported minimum`() {
        val timestamps = FrameSampler.timestamps(
            durationMs = 60_000L,
            scanWindowSeconds = 1,
            intervalMs = 2_000L,
        )

        assertEquals(listOf(0L, 2_000L, 4_000L), timestamps)
    }

    @Test
    fun `uses the default interval when the configured one is too small`() {
        val timestamps = FrameSampler.timestamps(
            durationMs = 60_000L,
            scanWindowSeconds = 5,
            intervalMs = 0L,
        )

        assertEquals(listOf(0L, 2_000L, 4_000L), timestamps)
    }

    @Test
    fun `caps the number of sampled frames`() {
        val timestamps = FrameSampler.timestamps(
            durationMs = 600_000L,
            scanWindowSeconds = 120,
            intervalMs = 250L,
        )

        assertEquals(FrameSampler.MAX_FRAMES, timestamps.size)
        assertEquals((FrameSampler.MAX_FRAMES - 1) * 250L, timestamps.last())
    }

    @Test
    fun `keeps one frame for a recording shorter than the interval`() {
        val timestamps = FrameSampler.timestamps(
            durationMs = 300L,
            scanWindowSeconds = 15,
            intervalMs = 2_000L,
        )

        assertEquals(listOf(0L), timestamps)
    }
}
