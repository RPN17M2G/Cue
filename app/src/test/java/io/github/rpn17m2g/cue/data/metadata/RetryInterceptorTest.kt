package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryInterceptorTest {

    @Test
    fun `waiting for a retry is cancelled in short poll steps`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(429))
            var call: okhttp3.Call? = null
            val sleeps = mutableListOf<Long>()
            val capture = Interceptor { chain ->
                call = chain.call()
                chain.proceed(chain.request())
            }
            val sleeper = Sleeper { millis ->
                sleeps += millis
                call?.cancel()
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(
                    RetryInterceptor(maxRetries = 2, backoffBaseMs = 250, sleeper = sleeper),
                )
                .addInterceptor(capture)
                .build()
            val request = Request.Builder().url(server.url("/")).build()

            val error = runCatching {
                client.newCall(request).execute().use { }
            }.exceptionOrNull()

            assertTrue(error is IOException)
            assertEquals(listOf(100L), sleeps)
        } finally {
            server.shutdown()
        }
    }
}
