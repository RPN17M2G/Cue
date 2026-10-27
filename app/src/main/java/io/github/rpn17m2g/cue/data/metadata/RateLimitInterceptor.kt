package io.github.rpn17m2g.cue.data.metadata

import okhttp3.Interceptor
import okhttp3.Response

/** Applies a provider-specific request pace, including retries replayed by OkHttp. */
class RateLimitInterceptor(
    val limiter: RateLimiter = RateLimiter(),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        limiter.acquire { chain.call().isCanceled() }
        return chain.proceed(chain.request())
    }
}
