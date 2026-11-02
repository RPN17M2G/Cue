package io.github.rpn17m2g.cue.domain.identify

/**
 * How much trust a lookup result earns, as the screen's "Check this" flag
 * needs it. The thresholds live here, not in the UI, so the banner and any
 * future auto-accept logic cannot drift apart (plan decision D15).
 */
enum class ConfidenceTier {
    /** Confident enough to accept silently. */
    ACCEPTED,

    /** Plausible but worth a human glance. */
    CHECK,

    /** Too weak to show as a match at all. */
    IGNORED,
}

object Confidence {

    const val ACCEPT_THRESHOLD = 0.75
    const val CHECK_THRESHOLD = 0.55

    fun tier(score: Double): ConfidenceTier = when {
        score >= ACCEPT_THRESHOLD -> ConfidenceTier.ACCEPTED
        score >= CHECK_THRESHOLD -> ConfidenceTier.CHECK
        else -> ConfidenceTier.IGNORED
    }
}
