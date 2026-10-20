package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CompositeMetadataResolverTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `uses primary results and does not call fallback`() = runTest {
        val primary = FakeProvider(
            IdentificationSource.ITUNES,
            LookupResult.Success(listOf(match("Hello", "Adele", 0.95))),
        )
        val fallback = FakeProvider(
            IdentificationSource.MUSICBRAINZ,
            LookupResult.Success(listOf(match("Hello", "Adele", 0.9, IdentificationSource.MUSICBRAINZ))),
        )
        val resolver = CompositeMetadataResolver(primary, fallback)

        val result = resolver.search(MetadataQuery("Hello", "Adele"))

        assertEquals(1, result.size)
        assertEquals(IdentificationSource.ITUNES, result.single().source)
        assertEquals(1, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `falls back to musicbrainz when itunes returns empty`() = runTest {
        val primary = FakeProvider(IdentificationSource.ITUNES, LookupResult.Success(emptyList()))
        val fallback = FakeProvider(
            IdentificationSource.MUSICBRAINZ,
            LookupResult.Success(
                listOf(match("Hello", "Adele", 0.9, IdentificationSource.MUSICBRAINZ)),
            ),
        )
        val resolver = CompositeMetadataResolver(primary, fallback)

        val result = resolver.search(MetadataQuery("Hello", "Adele"))

        assertEquals(1, result.size)
        assertEquals(IdentificationSource.MUSICBRAINZ, result.single().source)
        assertEquals(1, fallback.calls)
    }

    @Test
    fun `falls back to musicbrainz when itunes fails`() = runTest {
        val primary = FakeProvider(
            IdentificationSource.ITUNES,
            LookupResult.Failure(LookupError.Network()),
        )
        val fallback = FakeProvider(
            IdentificationSource.MUSICBRAINZ,
            LookupResult.Success(
                listOf(match("Hello", "Adele", 0.9, IdentificationSource.MUSICBRAINZ)),
            ),
        )
        val resolver = CompositeMetadataResolver(primary, fallback)

        val result = resolver.search(MetadataQuery("Hello", "Adele"))

        assertEquals(IdentificationSource.MUSICBRAINZ, result.single().source)
    }

    @Test
    fun `returns the fallback typed error when both providers fail`() = runTest {
        val primary = FakeProvider(
            IdentificationSource.ITUNES,
            LookupResult.Failure(LookupError.Network()),
        )
        val fallback = FakeProvider(
            IdentificationSource.MUSICBRAINZ,
            LookupResult.Failure(LookupError.RateLimited),
        )
        val resolver = CompositeMetadataResolver(primary, fallback)

        val result = resolver.searchResult(MetadataQuery("Hello", "Adele"))

        assertEquals(LookupError.RateLimited, (result as LookupResult.Failure).error)
        assertEquals(emptyList<Any>(), resolver.search(MetadataQuery("Hello", "Adele")))
    }

    @Test
    fun `deduplicates and sorts primary results by score`() = runTest {
        val primary = FakeProvider(
            IdentificationSource.ITUNES,
            LookupResult.Success(
                listOf(
                    match("Hello", "Adele", 0.5),
                    match("Hello", "Adele", 0.9),
                    match("Hello (Live)", "Adele", 0.6),
                ),
            ),
        )
        val fallback = FakeProvider(IdentificationSource.MUSICBRAINZ, LookupResult.Success(emptyList()))
        val resolver = CompositeMetadataResolver(primary, fallback)

        val result = resolver.search(MetadataQuery("Hello", "Adele"))

        assertEquals(2, result.size)
        assertEquals("Hello", result.first().title)
        assertEquals(0.9, result.first().score, 0.0001)
        assertEquals("Hello (Live)", result[1].title)
    }

    @Test
    fun `disabled gate returns empty without calling providers`() = runTest {
        val primary = FakeProvider(
            IdentificationSource.ITUNES,
            LookupResult.Success(listOf(match("Hello", "Adele", 1.0))),
        )
        val fallback = FakeProvider(
            IdentificationSource.MUSICBRAINZ,
            LookupResult.Success(listOf(match("Hello", "Adele", 1.0))),
        )
        val resolver = CompositeMetadataResolver(primary, fallback, NetworkGate.Disabled)

        val result = resolver.search(MetadataQuery("Hello", "Adele"))

        assertEquals(emptyList<Any>(), result)
        assertEquals(0, primary.calls)
        assertEquals(0, fallback.calls)
    }

    @Test
    fun `real clients fall back on an empty itunes response`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/search") -> jsonResponse("""{"resultCount":0,"results":[]}""")
                    path.startsWith("/ws/2/recording") -> jsonResponse(MB_RESPONSE)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val http = MetadataHttp(config = NetworkConfig(retries = 0), sleeper = Sleeper.None)
        val baseUrl = server.url("/").toString()
        val primary = ITunesClient(http.api(baseUrl, ITunesApi::class.java))
        val fallback = MusicBrainzClient(
            api = http.api(baseUrl, MusicBrainzApi::class.java),
        )
        val resolver = CompositeMetadataResolver(primary, fallback)

        val result = resolver.search(MetadataQuery("Bohemian Rhapsody", "Queen"))

        assertEquals(1, result.size)
        assertEquals(IdentificationSource.MUSICBRAINZ, result.single().source)
        assertEquals("Bohemian Rhapsody", result.single().title)
        assertTrue(server.requestCount >= 2)
    }

    private companion object {
        const val MB_RESPONSE = """
            {
              "recordings": [
                {
                  "id": "rec-1",
                  "title": "Bohemian Rhapsody",
                  "first-release-date": "1975-10-31",
                  "artist-credit": [{"name": "Queen", "artist": {"name": "Queen"}}],
                  "releases": [{"id": "release-1", "title": "A Night at the Opera", "date": "1975-11-21"}]
                }
              ]
            }
        """
    }
}
