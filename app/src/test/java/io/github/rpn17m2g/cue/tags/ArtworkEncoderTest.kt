package io.github.rpn17m2g.cue.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkEncoderTest {

    @Test
    fun `image within bounds is returned unchanged`() {
        val original = TestImages.png(width = 320, height = 200)

        val result = ArtworkEncoder.downscaleToJpeg(original)

        assertArrayEquals(original, result)
    }

    @Test
    fun `landscape image is scaled to the maximum width`() {
        val result = ArtworkEncoder.downscaleToJpeg(TestImages.png(width = 2000, height = 1000))

        val (width, height) = TestImages.size(result)
        assertEquals(1000, width)
        assertEquals(500, height)
        assertJpeg(result)
    }

    @Test
    fun `portrait image is scaled to the maximum height`() {
        val result = ArtworkEncoder.downscaleToJpeg(TestImages.png(width = 900, height = 1800))

        val (width, height) = TestImages.size(result)
        assertEquals(500, width)
        assertEquals(1000, height)
        assertJpeg(result)
    }

    @Test
    fun `square image keeps a one to one ratio`() {
        val result = ArtworkEncoder.downscaleToJpeg(TestImages.jpeg(width = 3000, height = 3000))

        val (width, height) = TestImages.size(result)
        assertEquals(1000, width)
        assertEquals(1000, height)
        assertJpeg(result)
    }

    @Test
    fun `custom maximum dimension is honoured`() {
        val result = ArtworkEncoder.downscaleToJpeg(
            TestImages.png(width = 600, height = 300),
            maxDimension = 300,
        )

        val (width, height) = TestImages.size(result)
        assertEquals(300, width)
        assertEquals(150, height)
        assertJpeg(result)
    }

    @Test
    fun `invalid image bytes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ArtworkEncoder.downscaleToJpeg(byteArrayOf(1, 2, 3, 4))
        }
    }

    private fun assertJpeg(bytes: ByteArray) {
        assertTrue("expected a JPEG signature", bytes.size > 2)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        assertEquals(0xFF, bytes[2].toInt() and 0xFF)
    }
}
