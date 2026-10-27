package io.github.rpn17m2g.cue.data.metadata

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataClientsTest {

    private fun intervals(client: OkHttpClient): List<Long> = client.interceptors
        .filterIsInstance<RateLimitInterceptor>()
        .map { interceptor -> interceptor.limiter.minIntervalMs }

    @Test
    fun `acoustid lookups are limited to three per minute`() {
        val clients = MetadataClients(
            policy = LookupPolicy(acoustIdKey = "key"),
            sleeper = Sleeper.None,
        )

        assertEquals(listOf(ProviderRateLimits.ACOUSTID_MIN_INTERVAL_MS), intervals(clients.acoustIdHttp))
        assertEquals(20_000L, ProviderRateLimits.ACOUSTID_MIN_INTERVAL_MS)
    }

    @Test
    fun `musicbrainz keeps its one request per second pace`() {
        val clients = MetadataClients(sleeper = Sleeper.None)

        assertEquals(listOf(ProviderRateLimits.MUSICBRAINZ_MIN_INTERVAL_MS), intervals(clients.musicBrainzHttp))
    }
}
