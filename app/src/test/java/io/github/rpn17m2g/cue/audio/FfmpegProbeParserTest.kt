package io.github.rpn17m2g.cue.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegProbeParserTest {

    private val videoWithAudio = """
        ffmpeg version 7.0 Copyright (c) 2000-2024 the FFmpeg developers
        Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'C:\Videos\שיר.mp4':
          Metadata:
            major_brand     : mp42
          Duration: 00:01:02.34, start: 0.000000, bitrate: 1200 kb/s
          Stream #0:0[0x1](und): Video: h264 (High) (avc1 / 0x31637661), yuv420p, 1080x1920, 60 fps
          Stream #0:1[0x2](und): Audio: aac (LC) (mp4a / 0x6134706D), 44100 Hz, stereo, fltp, 127 kb/s
        At least one output file must be specified
    """.trimIndent()

    @Test
    fun `parses duration and detects audio and video streams`() {
        val result = FfmpegProbeParser.parse(videoWithAudio)

        assertEquals(62_340L, result.durationMs)
        assertTrue(result.hasAudio)
        assertTrue(result.hasVideo)
        assertEquals("aac", result.audioCodec)
    }

    @Test
    fun `parses an audio-only input`() {
        val stderr = """
            Input #0, mp3, from 'song.mp3':
              Duration: 00:03:20.50, start: 0.025057, bitrate: 256 kb/s
              Stream #0:0: Audio: mp3, 44100 Hz, stereo, fltp, 256 kb/s
        """.trimIndent()

        val result = FfmpegProbeParser.parse(stderr)

        assertEquals(200_500L, result.durationMs)
        assertTrue(result.hasAudio)
        assertFalse(result.hasVideo)
        assertEquals("mp3", result.audioCodec)
    }

    @Test
    fun `reports no audio for a video-only input`() {
        val stderr = """
            Input #0, mp4, from 'silent.mp4':
              Duration: 00:00:10.00, start: 0.000000, bitrate: 900 kb/s
              Stream #0:0[0x1](und): Video: h264 (High), yuv420p, 1080x1920, 30 fps
        """.trimIndent()

        val result = FfmpegProbeParser.parse(stderr)

        assertEquals(10_000L, result.durationMs)
        assertFalse(result.hasAudio)
        assertTrue(result.hasVideo)
        assertNull(result.audioCodec)
    }

    @Test
    fun `handles an unavailable duration`() {
        val stderr = """
            Input #0, matroska,webm, from 'broken.mkv':
              Duration: N/A, start: 0.000000, bitrate: N/A
              Stream #0:0: Audio: opus, 48000 Hz, stereo, fltp
        """.trimIndent()

        val result = FfmpegProbeParser.parse(stderr)

        assertNull(result.durationMs)
        assertTrue(result.hasAudio)
        assertFalse(result.hasVideo)
        assertEquals("opus", result.audioCodec)
    }

    @Test
    fun `reports empty data when no streams are present`() {
        val result = FfmpegProbeParser.parse("No such file or directory")

        assertNull(result.durationMs)
        assertFalse(result.hasAudio)
        assertFalse(result.hasVideo)
        assertNull(result.audioCodec)
    }

    @Test
    fun `accepts stderr as individual lines`() {
        val result = FfmpegProbeParser.parse(
            listOf(
                "  Duration: 00:00:05.00, start: 0.000000, bitrate: 128 kb/s",
                "  Stream #0:0: Audio: pcm_s16le ([1][0][0][0] / 0x0001), 44100 Hz, mono",
            ),
        )

        assertEquals(5_000L, result.durationMs)
        assertEquals("pcm_s16le", result.audioCodec)
        assertTrue(result.hasAudio)
        assertFalse(result.hasVideo)
    }

    @Test
    fun `uses the first audio stream when several exist`() {
        val stderr = """
            Input #0, mkv, from 'multi.mkv':
              Duration: 01:00:00.01, start: 0.000000, bitrate: 5000 kb/s
              Stream #0:0: Video: h264 (High), yuv420p, 1920x1080
              Stream #0:1: Audio: aac (LC), 44100 Hz, stereo
              Stream #0:2: Audio: ac3, 48000 Hz, 5.1
        """.trimIndent()

        val result = FfmpegProbeParser.parse(stderr)

        assertEquals(3_600_010L, result.durationMs)
        assertEquals("aac", result.audioCodec)
        assertTrue(result.hasVideo)
    }

    @Test
    fun `does not mistake metadata mentioning audio for a stream`() {
        val stderr = """
            Input #0, mp4, from 'clip.mp4':
              Metadata:
                comment         : Audio: aac and Video: h264
              Duration: 00:00:01.00, start: 0.000000, bitrate: 10 kb/s
        """.trimIndent()

        val result = FfmpegProbeParser.parse(stderr)

        assertFalse(result.hasAudio)
        assertFalse(result.hasVideo)
        assertNull(result.audioCodec)
    }
}
