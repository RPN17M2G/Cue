package io.github.rpn17m2g.cue.data.input

import org.junit.Assert.assertEquals
import org.junit.Test

class InputKindDetectorTest {

    @Test
    fun `mp3 mime wins over everything else`() {
        assertEquals(
            InputKind.MP3,
            InputKindDetector.detect("audio/mpeg", "clip.mp4", null),
        )
    }

    @Test
    fun `mp3 extension is detected when the provider reports octet-stream`() {
        assertEquals(
            InputKind.MP3,
            InputKindDetector.detect("application/octet-stream", "Song.MP3", null),
        )
    }

    @Test
    fun `id3 header marks a file as mp3`() {
        assertEquals(
            InputKind.MP3,
            InputKindDetector.detect("video/mp4", "recording", "ID3\u0004".toByteArray()),
        )
    }

    @Test
    fun `mpeg frame sync marks a file as mp3`() {
        assertEquals(
            InputKind.MP3,
            InputKindDetector.detect(null, "recording", byteArrayOf(0xFF.toByte(), 0xFB.toByte())),
        )
    }

    @Test
    fun `anything else is treated as video`() {
        assertEquals(
            InputKind.VIDEO,
            InputKindDetector.detect("video/mp4", "recording.mp4", null),
        )
        assertEquals(
            InputKind.VIDEO,
            InputKindDetector.detect(null, "recording.webm", byteArrayOf(0, 0, 0, 0)),
        )
    }
}
