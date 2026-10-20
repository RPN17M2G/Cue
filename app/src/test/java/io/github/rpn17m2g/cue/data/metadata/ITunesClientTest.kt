package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ITunesClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ITunesClient
    private val sleeps = mutableListOf<Long>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = MetadataHttp(
            config = NetworkConfig(retries = 2),
            sleeper = Sleeper { millis -> sleeps += millis },
        )
        client = ITunesClient(http.api(server.url("/").toString(), ITunesApi::class.java))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps song results and upscales artwork`() = runTest {
        server.enqueue(jsonResponse(SONG_RESPONSE))
        val result = client.search(MetadataQuery(title = "Hello", artist = "Adele"))

        val matches = (result as LookupResult.Success).value
        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("Hello", match.title)
        assertEquals("Adele", match.artist)
        assertEquals("25", match.album)
        assertEquals("2015", match.year)
        assertEquals("Pop", match.genre)
        assertEquals(1, match.trackNumber)
        assertEquals("https://is1.example/600x600bb.jpg", match.artworkUrl)
        assertEquals(IdentificationSource.ITUNES, match.source)
        assertTrue(match.score > 0.9)
    }

    @Test
    fun `sorts matches by descending score`() = runTest {
        server.enqueue(jsonResponse(MULTI_RESPONSE))
        val matches = (client.search(MetadataQuery("Hello", "Adele")) as LookupResult.Success).value

        assertEquals("Hello", matches.first().title)
        assertTrue(matches[0].score >= matches[1].score)
        assertEquals("Hello (Live)", matches[1].title)
    }

    @Test
    fun `returns empty success when api has no results`() = runTest {
        server.enqueue(jsonResponse("""{"resultCount":0,"results":[]}"""))
        val result = client.search(MetadataQuery("Nothing", "Nobody"))

        assertEquals(emptyList<Any>(), (result as LookupResult.Success).value)
    }

    @Test
    fun `malformed json becomes a parse error`() = runTest {
        server.enqueue(jsonResponse("{this is not json"))
        val result = client.search(MetadataQuery("Hello", "Adele"))

        assertTrue((result as LookupResult.Failure).error is LookupError.Parse)
    }

    @Test
    fun `http 429 is retried and becomes rate limited`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) }

        val result = client.search(MetadataQuery("Hello", "Adele"))

        assertEquals(LookupError.RateLimited, (result as LookupResult.Failure).error)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(250L, 500L), sleeps)
    }

    @Test
    fun `retry succeeds after a transient 429`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(jsonResponse(SONG_RESPONSE))

        val result = client.search(MetadataQuery("Hello", "Adele"))

        assertTrue(result is LookupResult.Success)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(250L), sleeps)
    }

    @Test
    fun `non retryable http status becomes typed http error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.search(MetadataQuery("Hello", "Adele"))

        assertEquals(LookupError.Http(404), (result as LookupResult.Failure).error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `encodes hebrew queries in the url`() = runTest {
        server.enqueue(jsonResponse("""{"resultCount":0,"results":[]}"""))

        client.search(MetadataQuery("שלום עולם", "אמן"))

        val request = server.takeRequest()
        assertEquals("שלום עולם אמן", request.requestUrl!!.queryParameter("term"))
        assertFalse(request.path!!.contains("שלום"))
        assertEquals("song", request.requestUrl!!.queryParameter("entity"))
        assertEquals("10", request.requestUrl!!.queryParameter("limit"))
    }

    @Test
    fun `sends the descriptive user agent`() = runTest {
        server.enqueue(jsonResponse("""{"resultCount":0,"results":[]}"""))

        client.search(MetadataQuery("Hello"))

        val request = server.takeRequest()
        assertEquals(NetworkConfig.DEFAULT_USER_AGENT, request.getHeader("User-Agent"))
    }

    @Test
    fun `network gate blocks requests entirely`() = runTest {
        val gated = ITunesClient(
            api = MetadataHttp(sleeper = Sleeper.None)
                .api(server.url("/").toString(), ITunesApi::class.java),
            networkGate = NetworkGate.Disabled,
        )

        val result = gated.search(MetadataQuery("Hello"))

        assertEquals(emptyList<Any>(), (result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `artwork search uses album entity and upscales covers`() = runTest {
        server.enqueue(jsonResponse(ALBUM_RESPONSE))
        val candidates = client.search("25")

        val request = server.takeRequest()
        assertEquals("album", request.requestUrl!!.queryParameter("entity"))
        assertEquals(1, candidates.size)
        assertEquals("https://is1.example/600x600bb.jpg", candidates.single().artworkUrl600)
        assertEquals("25", candidates.single().album)
        assertEquals("Adele", candidates.single().artist)
    }

    @Test
    fun `artwork search falls back to song entity when albums are empty`() = runTest {
        server.enqueue(jsonResponse("""{"resultCount":0,"results":[]}"""))
        server.enqueue(jsonResponse(SONG_RESPONSE))

        val candidates = client.search("Hello")

        assertEquals("album", server.takeRequest().requestUrl!!.queryParameter("entity"))
        assertEquals("song", server.takeRequest().requestUrl!!.queryParameter("entity"))
        assertEquals("25", candidates.single().album)
    }

    @Test
    fun `missing title is skipped`() = runTest {
        server.enqueue(jsonResponse("""{"resultCount":1,"results":[{"artistName":"Nobody"}]}"""))

        val result = client.search(MetadataQuery("Hello")) as LookupResult.Success

        assertEquals(emptyList<Any>(), result.value)
    }

    private companion object {
        const val SONG_RESPONSE = """
            {
              "resultCount": 1,
              "results": [
                {
                  "trackName": "Hello",
                  "artistName": "Adele",
                  "collectionName": "25",
                  "releaseDate": "2015-10-23T07:00:00Z",
                  "primaryGenreName": "Pop",
                  "trackNumber": 1,
                  "artworkUrl100": "https://is1.example/100x100bb.jpg"
                }
              ]
            }
        """

        const val MULTI_RESPONSE = """
            {
              "resultCount": 2,
              "results": [
                {
                  "trackName": "Hello (Live)",
                  "artistName": "Adele",
                  "artworkUrl100": "https://is1.example/live/100x100bb.jpg"
                },
                {
                  "trackName": "Hello",
                  "artistName": "Adele",
                  "artworkUrl100": "https://is1.example/studio/100x100bb.jpg"
                }
              ]
            }
        """

        const val ALBUM_RESPONSE = """
            {
              "resultCount": 1,
              "results": [
                {
                  "collectionName": "25",
                  "artistName": "Adele",
                  "artworkUrl100": "https://is1.example/100x100bb.jpg"
                }
              ]
            }
        """
    }
}
