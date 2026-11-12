package io.github.rpn17m2g.cue.audio

import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TrimRange
import java.io.File
import java.io.IOException

object FfmpegCommandBuilder {

    const val DEFAULT_FINGERPRINT_DURATION_MS = 30_000L
    const val PROBE_EXPECTED_EXIT = 1

    private val basePrefix = listOf("-hide_banner", "-nostdin", "-y")
    private val progressPrefix = basePrefix + listOf("-nostats", "-progress", "pipe:2")

    private val nonFileOutputs = setOf(
        "-",
        "pipe:1",
        "pipe:2",
        "fd:1",
        "fd:2",
        "/dev/stdout",
        "/dev/stderr",
        "/dev/fd/1",
        "/dev/fd/2",
    )

    /**
     * Converts any FFmpeg-demuxable input to a stereo 44.1 kHz MP3. The seek is
     * placed before `-i` so FFmpeg jumps instead of decoding everything up to the
     * trim start; the duration is then relative to that seek point.
     */
    fun convertToMp3(
        inputPath: String,
        outputPath: String,
        trim: TrimRange?,
        quality: QualityPreset,
    ): List<String> {
        requireFileOutput(outputPath)
        requireNeverOverwritesInput(inputPath, outputPath)

        val args = progressPrefix.toMutableList()
        if (trim != null) {
            args += listOf("-ss", FfmpegTime.formatSeconds(trim.startMs))
        }
        args += listOf("-i", inputPath)
        if (trim != null) {
            args += listOf("-t", FfmpegTime.formatSeconds(trim.durationMs))
        }
        args += listOf(
            "-vn",
            "-map", "0:a:0",
            "-ac", "2",
            "-ar", "44100",
            "-c:a", "libmp3lame",
        )
        args += quality.ffmpegArgs
        args += listOf("-map_metadata", "-1", outputPath)
        return args
    }

    fun trimMp3(
        inputPath: String,
        outputPath: String,
        trim: TrimRange,
        quality: QualityPreset,
    ): List<String> = convertToMp3(inputPath, outputPath, trim, quality)

    /**
     * Chromaprint needs roughly 30 seconds of audio, and AcoustID only matches
     * audio from the beginning of the track. The server indexes the 0:10–0:25
     * part of each submitted fingerprint, so a slice taken from the middle of a
     * recording can never be found — verified against two commercial tracks,
     * where every mid-track window returned no results while the opening
     * window matched at 0.94–0.99. The slice therefore always starts at 0.
     *
     * The duration sent with the lookup is the length of the whole song when
     * it is known (see [TrackDuration]); a 30 second clip of a 3:24 song has to
     * be reported as 204 seconds, otherwise AcoustID filters out the recording.
     *
     * A recording often starts a few seconds before playback. AcoustID
     * tolerates that: the same audio scored 0.97 with a 2 second lead-in, 0.79
     * at 5 seconds and 0.68 at 8, so a short lead-in still identifies the song.
     * The lead-in is deliberately not trimmed: the packaged FFmpeg is a minimal
     * build with no `silenceremove`/`silencedetect` filter, and reading the
     * player clock with OCR to compute the offset was rejected as fragile.
     *
     * [outputPath] must be a real file: the process runner drains stdout, and
     * overwriting a stale temporary file is expected.
     */
    fun fingerprintCommand(
        inputPath: String,
        outputPath: String,
        sliceStartMs: Long = 0L,
        sliceDurationMs: Long = DEFAULT_FINGERPRINT_DURATION_MS,
    ): List<String> {
        requireFileOutput(outputPath)
        requireNeverOverwritesInput(inputPath, outputPath)
        require(sliceStartMs >= 0) { "sliceStartMs must be non-negative" }
        require(sliceDurationMs > 0) { "sliceDurationMs must be positive" }

        val args = basePrefix.toMutableList()
        args += listOf(
            "-ss", FfmpegTime.formatSeconds(sliceStartMs),
            "-t", FfmpegTime.formatSeconds(sliceDurationMs),
        )
        args += listOf(
            "-i",
            inputPath,
            "-vn",
            "-f",
            "chromaprint",
            // AcoustID expects the base64 form; make the muxer default explicit.
            "-fp_format",
            "base64",
            outputPath,
        )
        return args
    }

    /**
     * Probing has no output file, so FFmpeg exits with [PROBE_EXPECTED_EXIT] after
     * printing the input information to stderr. Do not treat that status as a
     * failure; combine it with the parsed result via [isProbeSuccess].
     */
    fun probeCommand(inputPath: String): List<String> = listOf("-hide_banner", "-nostdin", "-i", inputPath)

    fun isProbeSuccess(exitCode: Int, probeResult: ProbeResult): Boolean {
        val parsedInput = probeResult.durationMs != null || probeResult.hasAudio || probeResult.hasVideo
        return exitCode == PROBE_EXPECTED_EXIT && parsedInput
    }

    private fun requireFileOutput(outputPath: String) {
        require(outputPath.isNotBlank()) { "output must be a file path" }
        require(outputPath.trim().lowercase() !in nonFileOutputs) { "output must be a file path, not a stream" }
    }

    private fun requireNeverOverwritesInput(inputPath: String, outputPath: String) {
        val input = File(inputPath)
        val output = File(outputPath)
        val sameFile = try {
            input.canonicalFile == output.canonicalFile
        } catch (_: IOException) {
            input.absoluteFile == output.absoluteFile
        }
        require(!sameFile) { "output must not overwrite the input" }
    }
}
