package io.github.rpn17m2g.cue.data.metadata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `waits out the remainder of the interval using the injected clock`() = runTest {
        var now = 0L
        val waits = mutableListOf<Long>()
        val clock = WallClock { now }
        val delay = DelayProvider { millis ->
            waits += millis
            now += millis
        }
        val limiter = RateLimiter(minIntervalMs = 1_000L, clock = clock, delay = delay)

        limiter.acquire()
        assertEquals(emptyList<Long>(), waits)

        limiter.acquire()
        assertEquals(listOf(1_000L), waits)

        now = 5_000L
        limiter.acquire()
        assertEquals(listOf(1_000L), waits)
    }

    @Test
    fun `does not wait when enough time has already passed`() = runTest {
        var now = 10_000L
        var waited = 0L
        val limiter = RateLimiter(
            minIntervalMs = 1_000L,
            clock = WallClock { now },
            delay = DelayProvider { millis -> waited += millis },
        )

        limiter.acquire()
        now = 10_500L
        limiter.acquire()
        now = 12_000L
        limiter.acquire()

        assertEquals(500L, waited)
    }
}
