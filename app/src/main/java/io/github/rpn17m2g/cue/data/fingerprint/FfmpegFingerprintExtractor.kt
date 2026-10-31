package io.github.rpn17m2g.cue.data.fingerprint

import io.github.rpn17m2g.cue.audio.FfmpegCommandBuilder
import io.github.rpn17m2g.cue.audio.FfmpegRunner
import io.github.rpn17m2g.cue.domain.identify.AudioFingerprint
import io.github.rpn17m2g.cue.domain.identify.FingerprintExtractor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the packaged FFmpeg to write a base64 Chromaprint fingerprint for a short
 * slice of the input. A missing binary, a failed decode, or an empty or garbled
 * fingerprint degrades to null so the cascade can fall back to manual entry.
 *
 * The temporary fingerprint file lives in the cache and is removed on every path.
 */
class FfmpegFingerprintExtractor(
    private val runner: FfmpegRunner,
    private val outputDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FingerprintExtractor {

    override suspend fun extract(input: File, clipDurationMs: Long?): AudioFingerprint? =
        withContext(ioDispatcher) {
            val durationSeconds = acoustIdDurationSeconds(clipDurationMs)
                ?: return@withContext null
            outputDirectory.mkdirs()
            val output = File(outputDirectory, "${UUID.randomUUID()}.fingerprint")
            try {
                val args = FfmpegCommandBuilder.fingerprintCommand(
                    inputPath = input.absolutePath,
                    outputPath = output.absolutePath,
                    clipDurationMs = clipDurationMs,
                )
                if (runner.run(args) {} != 0) return@withContext null
                val value = output.takeIf { it.isFile }?.readText().orEmpty().trim()
                if (!FINGERPRINT.matches(value)) return@withContext null
                AudioFingerprint(value, durationSeconds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                output.delete()
            }
        }

    companion object {
        private val FINGERPRINT = Regex("^[A-Za-z0-9+/]+={0,2}$")
        private const val MIN_DURATION_MS = 1_000L
        private const val MIN_DURATION_SECONDS = 1

        /**
         * The duration AcoustID has to be told about: the length of the whole clip
         * the fingerprint came from, not the length of the sampled slice. AcoustID
         * narrows candidate recordings by duration, so reporting the 30 second
         * slice for a four minute track filters out the very recording being
         * looked up. Measured: the same fingerprint matched at 0.94 with the real
         * duration and returned no results at all with the slice length.
         *
         * An unknown clip length falls back to the slice length. That is only
         * correct when the clip really is that short, but the probe normally
         * knows the length and a lookup is still worth attempting.
         */
        fun acoustIdDurationSeconds(clipDurationMs: Long?): Int? {
            if (clipDurationMs == null) {
                return (FfmpegCommandBuilder.DEFAULT_FINGERPRINT_DURATION_MS / 1_000L).toInt()
            }
            if (clipDurationMs < MIN_DURATION_MS) return null
            return (clipDurationMs / 1_000L).toInt().coerceAtLeast(MIN_DURATION_SECONDS)
        }
    }
}
