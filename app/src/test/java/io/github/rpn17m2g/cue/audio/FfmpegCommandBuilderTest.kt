package io.github.rpn17m2g.cue.audio

import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TrimRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCommandBuilderTest {

    private val input = "C:\\Users\\Omer\\Videos\\recording.mp4"
    private val output = "C:\\Users\\Omer\\Music\\Cue\\song.mp3"

    @Test
    fun `convertToMp3 without trim omits seek and duration`() {
        val args = FfmpegCommandBuilder.convertToMp3(input, output, trim = null, QualityPreset.V0)

        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-y",
                "-i", input,
                "-vn", "-map", "0:a:0", "-ac", "2", "-ar", "44100",
                "-c:a", "libmp3lame", "-q:a", "0",
                "-map_metadata", "-1", output,
            ),
            args,
        )
        assertFalse(args.contains("-ss"))
        assertFalse(args.contains("-t"))
    }

    @Test
    fun `convertToMp3 with trim seeks before input and sets millisecond precision duration`() {
        val trim = TrimRange(startMs = 12_345, endMs = 42_345)

        val args = FfmpegCommandBuilder.convertToMp3(input, output, trim, QualityPreset.V2)

        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-y",
                "-ss", "12.345",
                "-i", input,
                "-t", "30.000",
                "-vn", "-map", "0:a:0", "-ac", "2", "-ar", "44100",
                "-c:a", "libmp3lame", "-q:a", "2",
                "-map_metadata", "-1", output,
            ),
            args,
        )
        assertTrue(args.indexOf("-ss") < args.indexOf("-i"))
    }

    @Test
    fun `convertToMp3 includes the quality arguments for every preset`() {
        QualityPreset.entries.forEach { preset ->
            val args = FfmpegCommandBuilder.convertToMp3(input, output, trim = null, preset)

            assertTrue(args.containsAll(preset.ffmpegArgs))
        }
    }

    @Test
    fun `convertToMp3 handles an audio-only input the same way`() {
        val mp3Input = "C:\\Music\\שיר מקורי.mp3"
        val mp3Output = "C:\\Music\\Cue\\שיר.mp3"

        val args = FfmpegCommandBuilder.convertToMp3(mp3Input, mp3Output, trim = null, QualityPreset.CBR_256)

        assertTrue(args.contains(mp3Input))
        assertTrue(args.contains(mp3Output))
        assertTrue(args.containsAll(listOf("-b:a", "256k")))
    }

    @Test
    fun `convertToMp3 keeps paths with spaces and quotes as single arguments`() {
        val trickyInput = "C:\\Users\\Omer\\My Videos\\שיר \"טוב\" 100%.mp4"
        val trickyOutput = "C:\\Users\\Omer\\My Music\\Cue\\שיר 'טוב' 100%.mp3"

        val args = FfmpegCommandBuilder.convertToMp3(trickyInput, trickyOutput, trim = null, QualityPreset.V0)

        assertEquals(1, args.count { it == trickyInput })
        assertEquals(1, args.count { it == trickyOutput })
    }

    @Test
    fun `convertToMp3 does not pass cover art because tags are written later`() {
        val withoutCover = FfmpegCommandBuilder.convertToMp3(input, output, null, QualityPreset.V0)
        val withCover = FfmpegCommandBuilder.convertToMp3(
            input,
            output,
            null,
            QualityPreset.V0,
            embedCoverPath = "C:\\covers\\cover.jpg",
        )

        assertEquals(withoutCover, withCover)
    }

    @Test
    fun `trimMp3 re-encodes using the same conversion path`() {
        val trim = TrimRange(startMs = 5_000, endMs = 15_000)

        assertEquals(
            FfmpegCommandBuilder.convertToMp3(input, output, trim, QualityPreset.CBR_192),
            FfmpegCommandBuilder.trimMp3(input, output, trim, QualityPreset.CBR_192),
        )
    }

    @Test
    fun `fingerprintCommand uses a twenty second slice thirty seconds long`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(input)

        assertEquals(
            listOf(
                "-ss", "20.000", "-t", "30.000",
                "-i", input,
                "-vn", "-f", "chromaprint", "-",
            ),
            args,
        )
    }

    @Test
    fun `fingerprintCommand samples from the start without a duration for clips shorter than fifty seconds`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(input, clipDurationMs = 49_999)

        assertEquals(
            listOf(
                "-ss", "0.000",
                "-i", input,
                "-vn", "-f", "chromaprint", "-",
            ),
            args,
        )
    }

    @Test
    fun `fingerprintCommand treats a fifty second clip as long enough for the standard slice`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(input, clipDurationMs = 50_000)

        assertTrue(args.containsAll(listOf("-ss", "20.000", "-t", "30.000")))
    }

    @Test
    fun `fingerprintCommand honours explicit slice bounds and output path`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(
            inputPath = input,
            outputPath = "fingerprint.txt",
            clipDurationMs = 120_000,
            sliceStartMs = 5_000,
            sliceDurationMs = 10_500,
        )

        assertEquals(
            listOf(
                "-ss", "5.000", "-t", "10.500",
                "-i", input,
                "-vn", "-f", "chromaprint", "fingerprint.txt",
            ),
            args,
        )
    }

    @Test
    fun `probeCommand probes the input and writes stream information to stderr`() {
        assertEquals(
            listOf("-hide_banner", "-nostdin", "-i", input),
            FfmpegCommandBuilder.probeCommand(input),
        )
    }
}
