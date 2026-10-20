package io.github.rpn17m2g.cue.audio

import io.github.rpn17m2g.cue.domain.model.QualityPreset
import io.github.rpn17m2g.cue.domain.model.TrimRange

object FfmpegCommandBuilder {

    const val DEFAULT_FINGERPRINT_START_MS = 20_000L
    const val DEFAULT_FINGERPRINT_DURATION_MS = 30_000L
    const val SHORT_CLIP_THRESHOLD_MS = 50_000L
    const val PROBE_EXPECTED_EXIT = 1

    private val commonPrefix = listOf(
        "-hide_banner",
        "-nostdin",
        "-y",
        "-nostats",
        "-progress", "pipe:2",
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
        requireNeverOverwritesInput(inputPath, outputPath)

        val args = commonPrefix.toMutableList()
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
     * Chromaprint needs roughly 30 seconds of audio. The default slice starts 20
     * seconds in, past intros and UI sounds. Clips shorter than 50 seconds use the
     * whole clip instead, because a 20 second offset would leave too little audio.
     *
     * [outputPath] must be a real file: the process runner drains stdout, so
     * writing the fingerprint to `-` would discard it.
     */
    fun fingerprintCommand(
        inputPath: String,
        outputPath: String,
        clipDurationMs: Long? = null,
        sliceStartMs: Long = DEFAULT_FINGERPRINT_START_MS,
        sliceDurationMs: Long = DEFAULT_FINGERPRINT_DURATION_MS,
    ): List<String> {
        require(outputPath.isNotBlank() && outputPath != "-") {
            "fingerprint output must be a file path, not stdout"
        }
        requireNeverOverwritesInput(inputPath, outputPath)
        require(sliceStartMs >= 0) { "sliceStartMs must be non-negative" }
        require(sliceDurationMs > 0) { "sliceDurationMs must be positive" }

        val args = mutableListOf<String>()
        val shortClip = clipDurationMs != null && clipDurationMs < SHORT_CLIP_THRESHOLD_MS
        if (shortClip) {
            args += listOf("-ss", FfmpegTime.formatSeconds(0))
        } else {
            args += listOf(
                "-ss", FfmpegTime.formatSeconds(sliceStartMs),
                "-t", FfmpegTime.formatSeconds(sliceDurationMs),
            )
        }
        args += listOf("-i", inputPath, "-vn", "-f", "chromaprint", outputPath)
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

    private fun requireNeverOverwritesInput(inputPath: String, outputPath: String) {
        require(inputPath != outputPath) { "output must not overwrite the input" }
    }
}
