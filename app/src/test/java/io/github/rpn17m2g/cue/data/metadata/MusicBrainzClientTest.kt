package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MusicBrainzClientTest {

    private lateinit var server: MockWebServer
    private lateinit var http: MetadataHttp
    private lateinit var client: MusicBrainzClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = MetadataHttp(
            config = NetworkConfig(retries = 0),
            sleeper = Sleeper.None,
        )
        client = MusicBrainzClient(
            api = http.api(server.url("/").toString(), MusicBrainzApi::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps recordings and leaves artwork empty without cover art`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        val matches = (client.search(MetadataQuery("Bohemian Rhapsody", "Queen")) as LookupResult.Success).value

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("Bohemian Rhapsody", match.title)
        assertEquals("Queen", match.artist)
        assertEquals("A Night at the Opera", match.album)
        assertEquals("1975", match.year)
        assertEquals(IdentificationSource.MUSICBRAINZ, match.source)
        assertEquals("rec-1", match.recordingMbid)
        assertEquals("release-1", match.releaseMbid)
        assertEquals("artist-1", match.artistMbid)
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
    fun `escapes lucene metacharacters in the query`() = runTest {
        server.enqueue(jsonResponse("""{"recordings":[]}"""))

        client.search(MetadataQuery("AC/DC: \"Back\" (Live)", "AC/DC"))

        val query = server.takeRequest().requestUrl!!.queryParameter("query")!!
        assertEquals(
            "recording:\"AC\\/DC\\: \\\"Back\\\" \\(Live\\)\" AND artist:\"AC\\/DC\"",
            query,
        )
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
        )

        val result = retrying.search(MetadataQuery("Anything"))

        assertEquals(LookupError.Http(503), (result as LookupResult.Failure).error)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `rate limit also applies to retries`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(jsonResponse(RESPONSE))
        var now = 0L
        val sleeps = mutableListOf<Long>()
        val clock = WallClock { now }
        val sleeper = Sleeper { millis ->
            sleeps += millis
            now += millis
        }
        val limitedClient = OkHttpClient.Builder()
            .addInterceptor(
                RetryInterceptor(maxRetries = 1, backoffBaseMs = 250, sleeper = sleeper),
            )
            .addInterceptor(
                RateLimitInterceptor(
                    RateLimiter(minIntervalMs = 1_000L, clock = clock, sleeper = sleeper),
                ),
            )
            .build()
        val limited = MusicBrainzClient(
            api = http.api(
                server.url("/").toString(),
                MusicBrainzApi::class.java,
                limitedClient,
            ),
        )

        val result = limited.search(MetadataQuery("Bohemian Rhapsody", "Queen"))

        assertTrue(result is LookupResult.Success)
        assertEquals(2, server.requestCount)
        assertTrue(sleeps.sum() >= 1_000L)
    }

    @Test
    fun `adds cover art archive url when a release exists`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().startsWith("/ws/2/recording") -> jsonResponse(RESPONSE)
                request.path.orEmpty().startsWith("/release/") -> MockResponse()
                    .setResponseCode(200)
                    .setBody("cover-bytes")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val coverArt = CoverArtArchiveClient(
            api = http.api(server.url("/").toString(), CoverArtArchiveApi::class.java),
        )
        val enriched = MusicBrainzClient(
            api = http.api(server.url("/").toString(), MusicBrainzApi::class.java),
            coverArtArchive = coverArt,
        )

        val match = (enriched.search(MetadataQuery("Bohemian Rhapsody", "Queen")) as LookupResult.Success)
            .value
            .single()

        assertEquals(
            "https://coverartarchive.org/release/release-1/front-500",
            match.artworkUrl,
        )
    }

    @Test
    fun `cover art archive 404 leaves artwork null`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().startsWith("/ws/2/recording") -> jsonResponse(RESPONSE)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val coverArt = CoverArtArchiveClient(
            api = http.api(server.url("/").toString(), CoverArtArchiveApi::class.java),
        )
        val enriched = MusicBrainzClient(
            api = http.api(server.url("/").toString(), MusicBrainzApi::class.java),
            coverArtArchive = coverArt,
        )

        val match = (enriched.search(MetadataQuery("Bohemian Rhapsody", "Queen")) as LookupResult.Success)
            .value
            .single()

        assertNull(match.artworkUrl)
    }

    @Test
    fun `enriches only the top three results and survives hanging cover art`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path.orEmpty().startsWith("/ws/2/recording") -> jsonResponse(FIVE_RECORDINGS)
                else -> MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            }
        }
        val shortHttp = MetadataHttp(
            config = NetworkConfig(timeoutMs = 150L, retries = 0),
            sleeper = Sleeper.None,
        )
        val coverArt = CoverArtArchiveClient(
            api = shortHttp.api(server.url("/").toString(), CoverArtArchiveApi::class.java),
        )
        val enriched = MusicBrainzClient(
            api = http.api(server.url("/").toString(), MusicBrainzApi::class.java),
            coverArtArchive = coverArt,
        )

        val startedAt = System.nanoTime()
        val result = enriched.search(MetadataQuery("Song 1", "Artist"))
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L

        val matches = (result as LookupResult.Success).value
        assertEquals(5, matches.size)
        assertTrue(matches.all { it.artworkUrl == null })
        assertEquals(4, server.requestCount)
        assertTrue("search took ${elapsedMs}ms", elapsedMs < 5_000L)
    }

    @Test
    fun `network gate blocks requests entirely`() = runTest {
        val gated = MusicBrainzClient(
            api = MetadataHttp(sleeper = Sleeper.None).api(
                server.url("/").toString(),
                MusicBrainzApi::class.java,
            ),
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

        const val FIVE_RECORDINGS = """
            {
              "recordings": [
                {
                  "id": "rec-1", "title": "Song 1", "score": 100,
                  "first-release-date": "2001-01-01",
                  "artist-credit": [{"name": "Artist"}],
                  "releases": [{"id": "release-1", "title": "Album 1", "date": "2001-01-01"}]
                },
                {
                  "id": "rec-2", "title": "Song 2", "score": 90,
                  "first-release-date": "2002-01-01",
                  "artist-credit": [{"name": "Artist"}],
                  "releases": [{"id": "release-2", "title": "Album 2", "date": "2002-01-01"}]
                },
                {
                  "id": "rec-3", "title": "Song 3", "score": 80,
                  "first-release-date": "2003-01-01",
                  "artist-credit": [{"name": "Artist"}],
                  "releases": [{"id": "release-3", "title": "Album 3", "date": "2003-01-01"}]
                },
                {
                  "id": "rec-4", "title": "Song 4", "score": 70,
                  "first-release-date": "2004-01-01",
                  "artist-credit": [{"name": "Artist"}],
                  "releases": [{"id": "release-4", "title": "Album 4", "date": "2004-01-01"}]
                },
                {
                  "id": "rec-5", "title": "Song 5", "score": 60,
                  "first-release-date": "2005-01-01",
                  "artist-credit": [{"name": "Artist"}],
                  "releases": [{"id": "release-5", "title": "Album 5", "date": "2005-01-01"}]
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
