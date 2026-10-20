package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MusicBrainzClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MusicBrainzClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = MetadataHttp(
            config = NetworkConfig(retries = 0),
            sleeper = Sleeper.None,
        )
        client = MusicBrainzClient(
            api = http.api(server.url("/").toString(), MusicBrainzApi::class.java),
            rateLimiter = RateLimiter(minIntervalMs = 0L),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps recordings and leaves artwork empty`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        val matches = (client.search(MetadataQuery("Bohemian Rhapsody", "Queen")) as LookupResult.Success).value

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("Bohemian Rhapsody", match.title)
        assertEquals("Queen", match.artist)
        assertEquals("A Night at the Opera", match.album)
        assertEquals("1975", match.year)
        assertEquals(IdentificationSource.MUSICBRAINZ, match.source)
        assertNull(match.artworkUrl)
        assertTrue(match.score > 0.9)
    }

    @Test
    fun `joins multiple artist credits`() = runTest {
        server.enqueue(jsonResponse(MULTI_ARTIST_RESPONSE))

        val match = (client.search(MetadataQuery("Under Pressure", "Queen")) as LookupResult.Success)
            .value
            .single()

        assertEquals("Queen & David Bowie", match.artist)
    }

    @Test
    fun `uses json format and sends user agent`() = runTest {
        server.enqueue(jsonResponse("""{"recordings":[]}"""))

        client.search(MetadataQuery("Anything"))

        val request = server.takeRequest()
        assertEquals("json", request.requestUrl!!.queryParameter("fmt"))
        assertEquals("10", request.requestUrl!!.queryParameter("limit"))
        assertEquals(NetworkConfig.DEFAULT_USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun `malformed json becomes a parse error`() = runTest {
        server.enqueue(jsonResponse("not-json-at-all"))

        val result = client.search(MetadataQuery("Anything"))

        assertTrue((result as LookupResult.Failure).error is LookupError.Parse)
    }

    @Test
    fun `http 503 is retried then surfaced as http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        val retrying = MusicBrainzClient(
            api = MetadataHttp(
                config = NetworkConfig(retries = 1),
                sleeper = Sleeper.None,
            ).api(server.url("/").toString(), MusicBrainzApi::class.java),
            rateLimiter = RateLimiter(minIntervalMs = 0L),
        )

        val result = retrying.search(MetadataQuery("Anything"))

        assertEquals(LookupError.Http(503), (result as LookupResult.Failure).error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `network gate blocks requests entirely`() = runTest {
        val gated = MusicBrainzClient(
            api = MetadataHttp(sleeper = Sleeper.None).api(
                server.url("/").toString(),
                MusicBrainzApi::class.java,
            ),
            rateLimiter = RateLimiter(minIntervalMs = 0L),
            networkGate = NetworkGate.Disabled,
        )

        val result = gated.search(MetadataQuery("Anything"))

        assertEquals(emptyList<Any>(), (result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `cover art archive helper builds release url`() {
        assertEquals(
            "https://coverartarchive.org/release/abc-123/front-500",
            CoverArtArchive.front500("abc-123"),
        )
    }

    private companion object {
        const val RESPONSE = """
            {
              "recordings": [
                {
                  "id": "rec-1",
                  "title": "Bohemian Rhapsody",
                  "score": 100,
                  "first-release-date": "1975-10-31",
                  "artist-credit": [
                    {"name": "Queen", "artist": {"id": "artist-1", "name": "Queen"}}
                  ],
                  "releases": [
                    {"id": "release-1", "title": "A Night at the Opera", "date": "1975-11-21"}
                  ]
                }
              ]
            }
        """

        const val MULTI_ARTIST_RESPONSE = """
            {
              "recordings": [
                {
                  "id": "rec-2",
                  "title": "Under Pressure",
                  "first-release-date": "1981-10-26",
                  "artist-credit": [
                    {"name": "Queen", "artist": {"id": "artist-1", "name": "Queen"}},
                    {"name": "David Bowie", "artist": {"id": "artist-2", "name": "David Bowie"}}
                  ],
                  "releases": [{"id": "release-2", "title": "Hot Space", "date": "1982-05-21"}]
                }
              ]
            }
        """
    }
}
