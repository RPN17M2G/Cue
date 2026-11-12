package io.github.rpn17m2g.cue.data.audio

import io.github.rpn17m2g.cue.audio.FfmpegRunner
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FfmpegAudioSampleExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `a clip shorter than the sample is used whole from the start`() {
        assertEquals(0L, FfmpegAudioSampleExtractor.startOffsetMs(8_000L))
        assertEquals(8_000L, FfmpegAudioSampleExtractor.sampleDurationMs(8_000L))
    }

    @Test
    fun `an unknown duration starts at zero and takes a full sample`() {
        assertEquals(0L, FfmpegAudioSampleExtractor.startOffsetMs(null))
        assertEquals(
            FfmpegAudioSampleExtractor.SAMPLE_DURATION_MS,
            FfmpegAudioSampleExtractor.sampleDurationMs(null),
        )
    }

    @Test
    fun `a long recording is sampled from the middle`() {
        // 3:24 track, 12 s sample: (204 - 12) / 2 = 96 s.
        assertEquals(96_000L, FfmpegAudioSampleExtractor.startOffsetMs(204_000L))
        assertEquals(12_000L, FfmpegAudioSampleExtractor.sampleDurationMs(204_000L))
    }

    @Test
    fun `extracts a mono 64 kbps mp3 and returns the file`() = runTest {
        val output = tempFolder.newFolder("out")
        val runner = FfmpegRunner { args, _ ->
            File(args.last()).writeBytes(ByteArray(1024))
            0
        }
        val extractor = FfmpegAudioSampleExtractor(runner, output)

        val sample = extractor.extract(inputFile(), durationMs = 204_000L)

        assertTrue(sample!!.isFile)
        assertTrue(sample.length() > 0L)
    }

    @Test
    fun `the command takes twelve seconds from the middle of a long track`() = runTest {
        val output = tempFolder.newFolder("out")
        var captured: List<String> = emptyList()
        val runner = FfmpegRunner { args, _ ->
            captured = args
            File(args.last()).writeBytes(ByteArray(1024))
            0
        }
        FfmpegAudioSampleExtractor(runner, output).extract(inputFile(), durationMs = 204_000L)

        assertEquals(listOf("-ss", "96.000", "-t", "12.000"), captured.subList(3, 7))
        assertTrue(captured.containsAll(listOf("-vn", "-ac", "1", "-ar", "44100")))
        assertEquals("libmp3lame", captured[captured.indexOf("-c:a") + 1])
        assertEquals("64k", captured[captured.indexOf("-b:a") + 1])
        assertTrue(captured.contains("-map_metadata"))
    }

    @Test
    fun `a failed ffmpeg run returns null and leaves no sample behind`() = runTest {
        val output = tempFolder.newFolder("out")
        val runner = FfmpegRunner { _, _ -> 1 }

        val sample = FfmpegAudioSampleExtractor(runner, output).extract(inputFile(), null)

        assertNull(sample)
        assertFalse(output.listFiles().orEmpty().any { it.name.endsWith(".mp3") })
    }

    @Test
    fun `a runner that creates nothing still returns null`() = runTest {
        val output = tempFolder.newFolder("out")
        val runner = FfmpegRunner { _, _ -> 0 }

        val sample = FfmpegAudioSampleExtractor(runner, output).extract(inputFile(), null)

        assertNull(sample)
    }

    @Test
    fun `a thrown runner failure returns null instead of propagating`() = runTest {
        val output = tempFolder.newFolder("out")
        val runner = FfmpegRunner { _, _ -> throw IllegalStateException("boom") }

        val sample = FfmpegAudioSampleExtractor(runner, output).extract(inputFile(), null)

        assertNull(sample)
    }

    private fun inputFile(): File = tempFolder.newFile("recording.mp4").apply { writeBytes(ByteArray(64)) }
}
