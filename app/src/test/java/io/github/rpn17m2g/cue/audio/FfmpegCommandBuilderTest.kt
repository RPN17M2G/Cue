package io.github.rpn17m2g.cue.audio

import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TrimRange
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCommandBuilderTest {

    private val input = "C:\\Users\\demo\\Videos\\recording.mp4"
    private val output = "C:\\Users\\demo\\Music\\Cue\\song.mp3"

    @Test
    fun `convertToMp3 without trim omits seek and duration`() {
        val args = FfmpegCommandBuilder.convertToMp3(input, output, trim = null, QualityPreset.V0)

        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-y", "-nostats", "-progress", "pipe:2",
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
                "-hide_banner", "-nostdin", "-y", "-nostats", "-progress", "pipe:2",
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
    fun `convertToMp3 emits live progress on stderr`() {
        val args = FfmpegCommandBuilder.convertToMp3(input, output, trim = null, QualityPreset.V0)

        assertEquals(listOf("-nostats", "-progress", "pipe:2"), args.subList(3, 6))
        assertFalse(args.contains("-stats"))
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
        val trickyInput = "C:\\Users\\demo\\My Videos\\שיר \"טוב\" 100%.mp4"
        val trickyOutput = "C:\\Users\\demo\\My Music\\Cue\\שיר 'טוב' 100%.mp3"

        val args = FfmpegCommandBuilder.convertToMp3(trickyInput, trickyOutput, trim = null, QualityPreset.V0)

        assertEquals(1, args.count { it == trickyInput })
        assertEquals(1, args.count { it == trickyOutput })
    }

    @Test
    fun `convertToMp3 refuses to overwrite the input`() {
        assertThrows(IllegalArgumentException::class.java) {
            FfmpegCommandBuilder.convertToMp3(input, input, trim = null, QualityPreset.V0)
        }
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
    fun `fingerprintCommand always samples from the start of the audio`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(input, outputPath = "fingerprint.tmp")

        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-y",
                "-ss", "0.000", "-t", "30.000",
                "-i", input,
                "-vn", "-f", "chromaprint", "-fp_format", "base64", "fingerprint.tmp",
            ),
            args,
        )
    }

    @Test
    fun `fingerprintCommand overwrites an existing output file`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(input, outputPath = "fingerprint.tmp")

        assertEquals(listOf("-hide_banner", "-nostdin", "-y"), args.take(3))
    }

    @Test
    fun `fingerprintCommand honours explicit slice bounds and output path`() {
        val args = FfmpegCommandBuilder.fingerprintCommand(
            inputPath = input,
            outputPath = "fingerprint.tmp",
            sliceStartMs = 5_000,
            sliceDurationMs = 10_500,
        )

        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-y",
                "-ss", "5.000", "-t", "10.500",
                "-i", input,
                "-vn", "-f", "chromaprint", "-fp_format", "base64", "fingerprint.tmp",
            ),
            args,
        )
    }

    @Test
    fun `both output commands reject non-file outputs`() {
        listOf("-", " ", "pipe:1", "fd:1", "/dev/stdout", "/dev/stderr").forEach { outputPath ->
            assertThrows("convertToMp3 accepted '$outputPath'", IllegalArgumentException::class.java) {
                FfmpegCommandBuilder.convertToMp3(input, outputPath, trim = null, QualityPreset.V0)
            }
            assertThrows("fingerprintCommand accepted '$outputPath'", IllegalArgumentException::class.java) {
                FfmpegCommandBuilder.fingerprintCommand(input, outputPath = outputPath)
            }
        }
    }

    @Test
    fun `fingerprintCommand refuses to overwrite the input`() {
        assertThrows(IllegalArgumentException::class.java) {
            FfmpegCommandBuilder.fingerprintCommand(input, outputPath = input)
        }
    }

    @Test
    fun `overwrite guard compares canonical paths`() {
        val directory = File("build/tmp/canonical-guard").apply { mkdirs() }
        val nested = File(directory, "nested").apply { mkdirs() }
        val inputFile = File(directory, "song.mp4").apply { writeText("audio") }
        val dotAlias = File(directory, "./song.mp4")
        val dotDotAlias = File(nested, "../song.mp4")

        assertThrows(IllegalArgumentException::class.java) {
            FfmpegCommandBuilder.convertToMp3(inputFile.path, dotAlias.path, trim = null, QualityPreset.V0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FfmpegCommandBuilder.fingerprintCommand(inputFile.path, outputPath = dotDotAlias.path)
        }
    }

    @Test
    fun `probeCommand probes the input and writes stream information to stderr`() {
        assertEquals(
            listOf("-hide_banner", "-nostdin", "-i", input),
            FfmpegCommandBuilder.probeCommand(input),
        )
    }

    @Test
    fun `probe success expects exit code one because ffmpeg requires an output`() {
        val valid = ProbeResult(durationMs = 10_000, hasAudio = true, hasVideo = true, audioCodec = "aac")
        val empty = ProbeResult(durationMs = null, hasAudio = false, hasVideo = false, audioCodec = null)

        assertEquals(1, FfmpegCommandBuilder.PROBE_EXPECTED_EXIT)
        assertTrue(FfmpegCommandBuilder.isProbeSuccess(FfmpegCommandBuilder.PROBE_EXPECTED_EXIT, valid))
        assertFalse(FfmpegCommandBuilder.isProbeSuccess(0, valid))
        assertFalse(FfmpegCommandBuilder.isProbeSuccess(FfmpegCommandBuilder.PROBE_EXPECTED_EXIT, empty))
    }
}
