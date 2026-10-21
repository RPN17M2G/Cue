package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertTrue(error is IOException)
        assertEquals(listOf(RateLimiter.CANCEL_POLL_MS), waits)
    }

    @Test
    fun `a cancelled waiter is not blocked by another waiter's sleep`() {
        val aSleeping = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val bCancelled = AtomicBoolean(false)
        val bError = AtomicReference<Throwable?>()
        val sleeper = Sleeper {
            if (Thread.currentThread().name == "waiter-a") {
                aSleeping.countDown()
                releaseA.await(5, TimeUnit.SECONDS)
            } else {
                bCancelled.set(true)
            }
        }
        val limiter = RateLimiter(
            minIntervalMs = 1_000L,
            clock = WallClock { 0L },
            sleeper = sleeper,
        )
        limiter.acquire()

        val waiterA = Thread({
            runCatching { limiter.acquire() }
        }, "waiter-a").apply {
            isDaemon = true
            start()
        }
        assertTrue(aSleeping.await(5, TimeUnit.SECONDS))

        val waiterB = Thread({
            runCatching { limiter.acquire { bCancelled.get() } }
                .exceptionOrNull()
                ?.let(bError::set)
        }, "waiter-b").apply {
            isDaemon = true
            start()
        }

        try {
            waiterB.join(2_000L)
            assertFalse("cancelled waiter stayed blocked", waiterB.isAlive)
            assertTrue(bError.get() is IOException)
        } finally {
            releaseA.countDown()
            waiterA.join(2_000L)
        }
    }
}
