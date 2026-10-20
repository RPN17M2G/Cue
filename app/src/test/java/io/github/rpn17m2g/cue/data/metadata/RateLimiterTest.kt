package io.github.rpn17m2g.cue.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `waits out the remainder of the interval using the injected clock`() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val limiter = RateLimiter(
            minIntervalMs = 1_000L,
            clock = WallClock { now },
            sleeper = Sleeper { millis ->
                waits += millis
                now += millis
            },
        )

        limiter.acquire()
        assertTrue(waits.isEmpty())

        limiter.acquire()
        assertEquals(1_000L, waits.sum())

        now = 5_000L
        limiter.acquire()
        assertEquals(1_000L, waits.sum())
    }

    @Test
    fun `does not wait when enough time has already passed`() {
        var now = 10_000L
        val waits = mutableListOf<Long>()
        val limiter = RateLimiter(
            minIntervalMs = 1_000L,
            clock = WallClock { now },
            sleeper = Sleeper { millis -> waits += millis },
        )

        limiter.acquire()
        now = 10_500L
        limiter.acquire()
        now = 12_000L
        limiter.acquire()

        assertEquals(500L, waits.sum())
    }

    @Test
    fun `cancellation aborts the wait in short steps`() {
        var now = 0L
        val waits = mutableListOf<Long>()
        val limiter = RateLimiter(
            minIntervalMs = 1_000L,
            clock = WallClock { now },
            sleeper = Sleeper { millis ->
                waits += millis
                now += millis
            },
        )
        limiter.acquire()

        val error = runCatching {
            limiter.acquire { waits.size >= 1 }
        }.exceptionOrNull()

        assertTrue(error is java.io.IOException)
        assertEquals(listOf(RateLimiter.CANCEL_POLL_MS), waits)
    }
}
