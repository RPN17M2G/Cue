package io.github.rpn17m2g.cue.data.audio

import io.github.rpn17m2g.cue.audio.FfmpegCommandBuilder
import io.github.rpn17m2g.cue.audio.FfmpegRunner
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the short MP3 that ACRCloud uploads. The caller owns the returned file
 * and must delete it; a failed extraction leaves nothing behind.
 */
class FfmpegAudioSampleExtractor(
    private val runner: FfmpegRunner,
    private val outputDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun extract(input: File, durationMs: Long?): File? = withContext(ioDispatcher) {
        outputDirectory.mkdirs()
        val output = File(outputDirectory, "${UUID.randomUUID()}.mp3")
        val startMs = startOffsetMs(durationMs)
        val lengthMs = sampleDurationMs(durationMs)
        try {
            val args = FfmpegCommandBuilder.acrCloudSampleCommand(
                inputPath = input.absolutePath,
                outputPath = output.absolutePath,
                startMs = startMs,
                durationMs = lengthMs,
            )
            if (runner.run(args) {} != 0) {
                output.delete()
                return@withContext null
            }
            output.takeIf { it.isFile && it.length() > 0L } ?: run {
                output.delete()
                null
            }
        } catch (cancelled: CancellationException) {
            output.delete()
            throw cancelled
        } catch (_: Exception) {
            output.delete()
            null
        }
    }

    companion object {

        /** ACRCloud asks for short samples; 12 s is enough and uploads quickly. */
        const val SAMPLE_DURATION_MS = FfmpegCommandBuilder.ACRCLOUD_SAMPLE_DURATION_MS

        /**
         * Samples the middle of the recording. A screen recording often starts
         * before playback, so the opening seconds may be silent; the middle is
         * far more likely to contain music. A clip shorter than the sample is
         * used whole.
         */
        fun startOffsetMs(durationMs: Long?): Long {
            val duration = durationMs?.takeIf { it > 0L } ?: return 0L
            if (duration <= SAMPLE_DURATION_MS) return 0L
            return ((duration - SAMPLE_DURATION_MS) / 2).coerceAtLeast(0L)
        }

        fun sampleDurationMs(durationMs: Long?): Long {
            val duration = durationMs?.takeIf { it > 0L } ?: return SAMPLE_DURATION_MS
            return minOf(duration, SAMPLE_DURATION_MS)
        }
    }
}
