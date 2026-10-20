package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException

/**
 * Serializes callers and enforces a minimum interval between requests. Production
 * defaults to a monotonic clock; tests inject both the clock and the sleeper.
 * Sleeping polls the caller's cancellation flag so a canceled call does not hold
 * the lock for the rest of the interval.
 */
class RateLimiter(
    private val minIntervalMs: Long = 1_000L,
    private val clock: WallClock = WallClock.Monotonic,
    private val sleeper: Sleeper = Sleeper.System,
) {
    init {
        require(minIntervalMs >= 0L) { "minIntervalMs must not be negative" }
    }

    private val lock = Any()
    private var lastRequestAtMillis: Long? = null

    fun acquire(isCanceled: () -> Boolean = { false }) {
        synchronized(lock) {
            val last = lastRequestAtMillis
            if (last != null) {
                var remaining = minIntervalMs - (clock.nowMillis() - last)
                while (remaining > 0L) {
                    if (isCanceled()) throw IOException("Canceled")
                    val step = minOf(remaining, CANCEL_POLL_MS)
                    sleeper.sleep(step)
                    remaining -= step
                }
            }
            lastRequestAtMillis = clock.nowMillis()
        }
    }

    companion object {
        const val CANCEL_POLL_MS = 100L
    }
}
