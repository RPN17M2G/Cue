package io.github.rpn17m2g.cue.audio

data class ProbeResult(
    val durationMs: Long?,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val audioCodec: String?,
)

object FfmpegProbeParser {

    private const val AUDIO_MARKER = "Audio:"
    private const val VIDEO_MARKER = "Video:"
    private const val STREAM_MARKER = "Stream #"
    private const val DURATION_MARKER = "Duration:"

    fun parse(stderr: String): ProbeResult = parse(stderr.split('\r', '\n'))

    fun parse(lines: Iterable<String>): ProbeResult {
        var durationMs: Long? = null
        var durationSeen = false
        var hasAudio = false
        var hasVideo = false
        var audioCodec: String? = null

        for (line in lines) {
            if (!durationSeen) {
                val durationIndex = line.indexOf(DURATION_MARKER)
                if (durationIndex >= 0) {
                    durationSeen = true
                    durationMs = FfmpegTime.parseClock(line.substring(durationIndex + DURATION_MARKER.length))
                }
            }

            if (!line.contains(STREAM_MARKER)) continue

            if (line.contains(AUDIO_MARKER)) {
                hasAudio = true
                if (audioCodec == null) {
                    audioCodec = line.substringAfter(AUDIO_MARKER)
                        .trim()
                        .substringBefore(' ')
                        .substringBefore(',')
                        .ifEmpty { null }
                }
            }
            if (line.contains(VIDEO_MARKER)) hasVideo = true
        }

        return ProbeResult(
            durationMs = durationMs,
            hasAudio = hasAudio,
            hasVideo = hasVideo,
            audioCodec = audioCodec,
        )
    }
}
