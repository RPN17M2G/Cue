package io.github.rpn17m2g.cue.data.metadata

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeezerClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DeezerClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DeezerClient(
            api = MetadataHttp(
                config = NetworkConfig(retries = 0),
                sleeper = Sleeper.None,
            ).api(server.url("/").toString(), DeezerApi::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps bpm gain and isrc from the best match`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertEquals("By Your Side", track?.title)
        assertEquals("Jonas Blue", track?.artist)
        assertEquals(124, track?.bpm)
        assertEquals(-6.3, track?.replayGainDb!!, 0.0001)
        assertEquals("GBUM71700001", track?.isrc)
    }

    @Test
    fun `picks the closest result instead of the first one`() = runTest {
        server.enqueue(jsonResponse(COVERS_FIRST_RESPONSE))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertEquals("Jonas Blue", track?.artist)
        assertEquals(124, track?.bpm)
    }

    @Test
    fun `a result with a different title is rejected`() = runTest {
        server.enqueue(jsonResponse(WRONG_TITLE_RESPONSE))

        assertNull(client.lookup("By Your Side", "Jonas Blue").success())
    }

    @Test
    fun `a result with a completely different artist is rejected`() = runTest {
        server.enqueue(jsonResponse(WRONG_ARTIST_RESPONSE))

        assertNull(client.lookup("By Your Side", "Jonas Blue").success())
    }

    @Test
    fun `rounds a fractional bpm to a whole number`() = runTest {
        server.enqueue(jsonResponse(FRACTIONAL_BPM_RESPONSE))

        assertEquals(125, client.lookup("Song", "Artist").success()?.bpm)
    }

    @Test
    fun `drops non-positive values`() = runTest {
        server.enqueue(jsonResponse(ZERO_VALUES_RESPONSE))

        val track = client.lookup("Song", "Artist").success()

        assertNull(track?.bpm)
        assertEquals("Song", track?.title)
    }

    @Test
    fun `sends the search query and a limit`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))

        client.lookup("By Your Side", "Jonas Blue")

        val url = server.takeRequest().requestUrl!!
        assertEquals("/search", url.encodedPath)
        assertEquals("By Your Side Jonas Blue", url.queryParameter("q"))
        assertEquals("10", url.queryParameter("limit"))
    }

    @Test
    fun `an empty result list is a successful empty answer`() = runTest {
        server.enqueue(jsonResponse("""{"data":[]}"""))

        assertNull(client.lookup("Song", "Artist").success())
    }

    @Test
    fun `an http failure is surfaced as a typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client.lookup("Song", "Artist")

        assertEquals(LookupError.Http(500), (result as LookupResult.Failure).error)
    }

    @Test
    fun `a blank title never touches the network`() = runTest {
        val result = client.lookup("  ", "Artist")

        assertNull((result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a closed network gate never calls the provider`() = runTest {
        val gated = DeezerClient(
            api = MetadataHttp(sleeper = Sleeper.None)
                .api(server.url("/").toString(), DeezerApi::class.java),
            networkGate = NetworkGate.Disabled,
        )

        val result = gated.lookup("Song", "Artist")

        assertNull((result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `lookupOrNull collapses a failure to nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(client.lookupOrNull("Song", "Artist"))
    }

    private fun LookupResult<DeezerTrack?>.success(): DeezerTrack? =
        (this as LookupResult.Success).value

    private companion object {
        const val SEARCH_RESPONSE = """
            {
              "data": [
                {
                  "title": "By Your Side",
                  "bpm": 124.0,
                  "gain": -6.3,
                  "isrc": "GBUM71700001",
                  "artist": {"name": "Jonas Blue"}
                }
              ]
            }
        """

        const val COVERS_FIRST_RESPONSE = """
            {
              "data": [
                {
                  "title": "By Your Side",
                  "bpm": 90.0,
                  "gain": -2.0,
                  "isrc": "COVER000001",
                  "artist": {"name": "Some Cover Band"}
                },
                {
                  "title": "By Your Side",
                  "bpm": 124.0,
                  "gain": -6.3,
                  "isrc": "GBUM71700001",
                  "artist": {"name": "Jonas Blue"}
                }
              ]
            }
        """

        const val WRONG_TITLE_RESPONSE = """
            {
              "data": [
                {
                  "title": "Completely Different",
                  "bpm": 120.0,
                  "artist": {"name": "Jonas Blue"}
                }
              ]
            }
        """

        const val WRONG_ARTIST_RESPONSE = """
            {
              "data": [
                {
                  "title": "By Your Side",
                  "bpm": 120.0,
                  "artist": {"name": "Someone Else Entirely"}
                }
              ]
            }
        """

        const val FRACTIONAL_BPM_RESPONSE = """
            {
              "data": [
                {
                  "title": "Song",
                  "bpm": 124.6,
                  "artist": {"name": "Artist"}
                }
              ]
            }
        """

        const val ZERO_VALUES_RESPONSE = """
            {
              "data": [
                {
                  "title": "Song",
                  "bpm": 0.0,
                  "gain": null,
                  "isrc": "",
                  "artist": {"name": "Artist"}
                }
              ]
            }
        """
    }
}
