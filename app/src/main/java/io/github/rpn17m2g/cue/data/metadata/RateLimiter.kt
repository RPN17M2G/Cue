package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException

/**
 * Serializes callers and enforces a minimum interval between requests. A caller
 * reserves its slot under the lock and then sleeps after releasing it, so other
 * waiters can observe cancellation immediately. Production defaults to a
 * monotonic clock; tests inject both the clock and the sleeper.
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
    private var nextAvailableAtMillis: Long? = null

    fun acquire(isCanceled: () -> Boolean = { false }) {
        if (isCanceled()) throw IOException("Canceled")

        val waitMillis = synchronized(lock) {
            val now = clock.nowMillis()
            val earliest = nextAvailableAtMillis ?: now
            val wait = (earliest - now).coerceAtLeast(0L)
            nextAvailableAtMillis = maxOf(now, earliest) + minIntervalMs
            wait
        }

        sleepCancellable(waitMillis, isCanceled)

        if (isCanceled()) throw IOException("Canceled")
    }

    private fun sleepCancellable(totalMillis: Long, isCanceled: () -> Boolean) {
        var remaining = totalMillis
        while (remaining > 0L) {
            if (isCanceled()) throw IOException("Canceled")
            val step = minOf(remaining, CANCEL_POLL_MS)
            sleeper.sleep(step)
            remaining -= step
        }
    }

    companion object {
        const val CANCEL_POLL_MS = 100L
    }
}
