package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries transient failures with exponential backoff. The sleeping strategy is
 * injected so JVM tests can record delays instead of waiting.
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
                sleeper.sleep(backoffMillis(attempt))
                attempt++
                continue
            }

            if (!isRetryable(response.code) || attempt >= maxRetries) return response

            val waitMillis = retryAfterMillis(response) ?: backoffMillis(attempt)
            response.close()
            sleeper.sleep(waitMillis)
            attempt++
        }
    }

    private fun backoffMillis(attempt: Int): Long = backoffBaseMs * (1L shl attempt)

    private fun isRetryable(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    private fun canReplay(request: okhttp3.Request): Boolean =
        request.method == "GET" || request.method == "HEAD" || request.body == null

    private fun retryAfterMillis(response: Response): Long? =
        response.header("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it >= 0L }
            ?.let { seconds -> seconds * 1_000L }
}
