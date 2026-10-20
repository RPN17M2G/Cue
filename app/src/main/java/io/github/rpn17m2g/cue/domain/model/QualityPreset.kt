package io.github.rpn17m2g.cue.domain.model

/**
 * Output quality presets exposed on the review screen. Bitrates are the averages
 * used only for file-size estimates; FFmpeg receives the actual arguments.
 */
enum class QualityPreset(
    val approximateKbps: Int,
    val ffmpegArgs: List<String>,
) {
    V0(approximateKbps = 245, ffmpegArgs = listOf("-q:a", "0")),
    V2(approximateKbps = 190, ffmpegArgs = listOf("-q:a", "2")),
    CBR_256(approximateKbps = 256, ffmpegArgs = listOf("-b:a", "256k")),
    CBR_192(approximateKbps = 192, ffmpegArgs = listOf("-b:a", "192k")),
}
