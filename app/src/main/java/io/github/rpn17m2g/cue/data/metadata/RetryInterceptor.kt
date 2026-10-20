package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Retries transient failures with exponential backoff. Only replayable requests
 * are retried, so an AcoustID POST is never sent twice by accident. Backoff waits
 * are capped and poll cancellation in short steps.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val backoffBaseMs: Long = 250L,
    private val sleeper: Sleeper = Sleeper.System,
) : Interceptor {

    init {
        require(maxRetries >= 0) { "maxRetries must not be negative" }
        require(backoffBaseMs >= 0L) { "backoffBaseMs must not be negative" }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (error: IOException) {
                if (attempt >= maxRetries || !canReplay(request)) throw error
                sleepCancellable(chain, backoffMillis(attempt))
                attempt++
                continue
            }

            if (!isRetryable(response.code) || attempt >= maxRetries || !canReplay(request)) {
                return response
            }

            val waitMillis = retryAfterMillis(response) ?: backoffMillis(attempt)
            response.close()
            sleepCancellable(chain, waitMillis)
            attempt++
        }
    }

    private fun sleepCancellable(chain: Interceptor.Chain, totalMillis: Long) {
        var remaining = totalMillis
        while (remaining > 0L) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val step = minOf(remaining, CANCEL_POLL_MS)
            sleeper.sleep(step)
            remaining -= step
        }
    }

    private fun backoffMillis(attempt: Int): Long = backoffBaseMs * (1L shl attempt)

    private fun isRetryable(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    private fun canReplay(request: Request): Boolean =
        request.method == "GET" || request.method == "HEAD" || request.body == null

    private fun retryAfterMillis(response: Response): Long? {
        val raw = response.header("Retry-After")?.trim() ?: return null
        val seconds = raw.toLongOrNull() ?: return null
        if (seconds < 0L) return null
        return (seconds * MILLIS_PER_SECOND).coerceAtMost(MAX_RETRY_AFTER_MS)
    }

    private companion object {
        const val CANCEL_POLL_MS = 100L
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_RETRY_AFTER_MS = 30_000L
    }
}
