package io.github.rpn17m2g.cue.domain.identify

import org.junit.Assert.assertEquals
import org.junit.Test

class StageTimingsTest {

    @Test
    fun `total adds every stage`() {
        val timings = StageTimings(
            framesMs = 100,
            ocrMs = 200,
            cascadeMs = 30,
            enrichmentMs = 5,
        )

        assertEquals(300, timings.localMs)
        assertEquals(335, timings.totalMs)
    }

    @Test
    fun `per frame cost divides the frame time by the count`() {
        assertEquals(150, StageTimings(framesMs = 1_500, frameCount = 10).perFrameMs)
    }

    @Test
    fun `per frame cost is zero when nothing was sampled`() {
        assertEquals(0, StageTimings(framesMs = 1_500).perFrameMs)
    }
}
