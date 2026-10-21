package io.github.rpn17m2g.cue.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkEncoderTest {

    private val encoder = ImageIoArtworkEncoder()

    @Test
    fun `image within bounds is still encoded as jpeg`() {
        val result = encoder.downscaleToJpeg(TestImages.png(width = 320, height = 200))

        val (width, height) = TestImages.size(result.bytes)
        assertEquals(320, width)
        assertEquals(200, height)
        assertEquals("image/jpeg", result.mimeType)
        assertJpeg(result.bytes)
    }

    @Test
    fun `small png becomes jpeg rather than a png labelled as jpeg`() {
        val result = encoder.downscaleToJpeg(TestImages.png(width = 24, height = 16))

        assertEquals("image/jpeg", result.mimeType)
        assertJpeg(result.bytes)
    }

    @Test
    fun `landscape image is scaled to the maximum width`() {
        val result = encoder.downscaleToJpeg(TestImages.png(width = 2000, height = 1000))

        val (width, height) = TestImages.size(result.bytes)
        assertEquals(1000, width)
        assertEquals(500, height)
        assertJpeg(result.bytes)
    }

    @Test
    fun `portrait image is scaled to the maximum height`() {
        val result = encoder.downscaleToJpeg(TestImages.png(width = 900, height = 1800))

        val (width, height) = TestImages.size(result.bytes)
        assertEquals(500, width)
        assertEquals(1000, height)
        assertJpeg(result.bytes)
    }

    @Test
    fun `square image keeps a one to one ratio`() {
        val result = encoder.downscaleToJpeg(TestImages.jpeg(width = 3000, height = 3000))

        val (width, height) = TestImages.size(result.bytes)
        assertEquals(1000, width)
        assertEquals(1000, height)
        assertJpeg(result.bytes)
    }

    @Test
    fun `custom maximum dimension is honoured`() {
        val result = encoder.downscaleToJpeg(
            TestImages.png(width = 600, height = 300),
            maxDimension = 300,
        )

        val (width, height) = TestImages.size(result.bytes)
        assertEquals(300, width)
        assertEquals(150, height)
        assertJpeg(result.bytes)
    }

    @Test
    fun `invalid image bytes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            encoder.downscaleToJpeg(byteArrayOf(1, 2, 3, 4))
        }
    }

    private fun assertJpeg(bytes: ByteArray) {
        assertTrue("expected a JPEG signature", bytes.size > 2)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        assertEquals(0xFF, bytes[2].toInt() and 0xFF)
    }
}
