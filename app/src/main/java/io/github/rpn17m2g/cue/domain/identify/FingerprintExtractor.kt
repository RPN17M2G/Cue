package io.github.rpn17m2g.cue.domain.identify

import java.io.File

/** A Chromaprint fingerprint and the duration of the audio it was computed over. */
data class AudioFingerprint(
    val value: String,
    /**
     * Length of the whole clip the fingerprint was sampled from, not of the
     * sampled slice: AcoustID compares it against recording lengths.
     */
    val durationSeconds: Int,
)

/**
 * Produces an AcoustID-compatible fingerprint for a short slice of the input.
 * Returning null means fingerprint lookup is unavailable for this file (missing
 * binary, unreadable audio, or an unusable fingerprint), not that no song matched.
 */
fun interface FingerprintExtractor {
    suspend fun extract(input: File, clipDurationMs: Long?): AudioFingerprint?
}
