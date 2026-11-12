package io.github.rpn17m2g.cue.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameTargetSizeTest {

    @Test
    fun `a small frame keeps its size`() {
        assertEquals(FrameSize(1_280, 720), frameTargetSize(1_280, 720, rotationDegrees = 0))
        assertEquals(FrameSize(640, 480), frameTargetSize(640, 480, rotationDegrees = 0))
    }

    @Test
    fun `a tall phone frame is scaled down on its long edge`() {
        assertEquals(FrameSize(576, 1_280), frameTargetSize(1_080, 2_400, rotationDegrees = 0))
    }

    @Test
    fun `a landscape frame is scaled down on its long edge`() {
        assertEquals(FrameSize(1_280, 720), frameTargetSize(1_920, 1_080, rotationDegrees = 0))
    }

    @Test
    fun `a 90 degree rotation swaps the metadata dimensions`() {
        assertEquals(FrameSize(720, 1_280), frameTargetSize(1_920, 1_080, rotationDegrees = 90))
    }

    @Test
    fun `a 270 degree rotation also swaps the metadata dimensions`() {
        assertEquals(FrameSize(720, 1_280), frameTargetSize(1_920, 1_080, rotationDegrees = 270))
    }

    @Test
    fun `a 180 degree rotation keeps the metadata dimensions`() {
        assertEquals(FrameSize(1_280, 720), frameTargetSize(1_920, 1_080, rotationDegrees = 180))
    }

    @Test
    fun `an invalid size returns null`() {
        assertNull(frameTargetSize(0, 1_080, rotationDegrees = 0))
        assertNull(frameTargetSize(1_920, 0, rotationDegrees = 0))
        assertNull(frameTargetSize(1_920, 1_080, rotationDegrees = 0, maxLongEdge = 0))
    }

    @Test
    fun `a custom long edge is honoured`() {
        assertEquals(FrameSize(480, 320), frameTargetSize(1_920, 1_280, 0, maxLongEdge = 480))
    }
}
