package io.github.rpn17m2g.cue.domain.identify

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfidenceTest {

    @Test
    fun `tiers use the plan thresholds`() {
        assertEquals(ConfidenceTier.ACCEPTED, Confidence.tier(0.75))
        assertEquals(ConfidenceTier.ACCEPTED, Confidence.tier(1.0))
        assertEquals(ConfidenceTier.CHECK, Confidence.tier(0.74))
        assertEquals(ConfidenceTier.CHECK, Confidence.tier(0.55))
        assertEquals(ConfidenceTier.IGNORED, Confidence.tier(0.54))
        assertEquals(ConfidenceTier.IGNORED, Confidence.tier(0.0))
    }
}
