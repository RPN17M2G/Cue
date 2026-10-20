package io.github.rpn17m2g.cue.data.metadata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes callers and enforces a minimum interval between requests. MusicBrainz
 * asks for at most one request per second; both the clock and the delay are
 * injectable so tests never wait for wall time.
 */
class RateLimiter(
    private val minIntervalMs: Long = 1_000L,
    private val clock: WallClock = WallClock.System,
    private val delay: DelayProvider = DelayProvider.Default,
) {
    init {
        require(minIntervalMs >= 0L) { "minIntervalMs must not be negative" }
    }

    private val mutex = Mutex()
    private var lastRequestAtMillis: Long? = null

    suspend fun acquire() {
        mutex.withLock {
            val last = lastRequestAtMillis
            if (last != null) {
                val remaining = minIntervalMs - (clock.nowMillis() - last)
                if (remaining > 0L) delay.delay(remaining)
            }
            lastRequestAtMillis = clock.nowMillis()
        }
    }
}
