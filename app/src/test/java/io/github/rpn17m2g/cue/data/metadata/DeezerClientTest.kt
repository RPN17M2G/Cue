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
        server.enqueue(jsonResponse(TRACK_RESPONSE))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertEquals("By Your Side", track?.title)
        assertEquals("Jonas Blue", track?.artist)
        assertEquals(124, track?.bpm)
        assertEquals(-6.3, track?.replayGainDb!!, 0.0001)
        assertEquals("GBUM71700001", track?.isrc)
    }

    @Test
    fun `reads bpm and gain from the track endpoint the search does not fill`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))
        server.enqueue(jsonResponse(TRACK_RESPONSE))

        client.lookup("By Your Side", "Jonas Blue")

        assertEquals("/search", server.takeRequest().requestUrl!!.encodedPath)
        val detail = server.takeRequest()
        assertEquals("/track/1", detail.requestUrl!!.encodedPath)
    }

    @Test
    fun `a failed detail call keeps the names and isrc from the search`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))
        server.enqueue(MockResponse().setResponseCode(500))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertEquals("By Your Side", track?.title)
        assertEquals("GBUM71700001", track?.isrc)
        assertNull(track?.bpm)
        assertNull(track?.replayGainDb)
    }

    @Test
    fun `an unusable detail answer keeps the search values`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))
        server.enqueue(jsonResponse("""{"error":{"type":"DataException","message":"no data"}}"""))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertEquals("By Your Side", track?.title)
        assertEquals("GBUM71700001", track?.isrc)
        assertNull(track?.bpm)
    }

    @Test
    fun `picks the closest result instead of the first one`() = runTest {
        server.enqueue(jsonResponse(COVERS_FIRST_RESPONSE))
        server.enqueue(jsonResponse(TRACK_RESPONSE))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertEquals("Jonas Blue", track?.artist)
        assertEquals(124, track?.bpm)
        server.takeRequest()
        assertEquals("/track/2", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `a result with a different title is rejected`() = runTest {
        server.enqueue(jsonResponse(WRONG_TITLE_RESPONSE))

        assertNull(client.lookup("By Your Side", "Jonas Blue").success())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a result with a completely different artist is rejected`() = runTest {
        server.enqueue(jsonResponse(WRONG_ARTIST_RESPONSE))

        assertNull(client.lookup("By Your Side", "Jonas Blue").success())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `rounds a fractional bpm to a whole number`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))
        server.enqueue(jsonResponse(TRACK_RESPONSE.replace("124.0", "124.6")))

        assertEquals(125, client.lookup("By Your Side", "Jonas Blue").success()?.bpm)
    }

    @Test
    fun `drops non-positive values`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))
        server.enqueue(jsonResponse(ZERO_VALUES_TRACK_RESPONSE))

        val track = client.lookup("By Your Side", "Jonas Blue").success()

        assertNull(track?.bpm)
        assertEquals("By Your Side", track?.title)
    }

    @Test
    fun `sends the search query and a limit`() = runTest {
        server.enqueue(jsonResponse(SEARCH_RESPONSE))
        server.enqueue(jsonResponse(TRACK_RESPONSE))

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
        assertEquals(1, server.requestCount)
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
        /** The search endpoint omits BPM and gain; this mirrors a real answer. */
        const val SEARCH_RESPONSE = """
            {
              "data": [
                {
                  "id": 1,
                  "title": "By Your Side",
                  "isrc": "GBUM71700001",
                  "artist": {"name": "Jonas Blue"}
                }
              ]
            }
        """

        const val TRACK_RESPONSE = """
            {
              "id": 1,
              "title": "By Your Side",
              "bpm": 124.0,
              "gain": -6.3,
              "isrc": "GBUM71700001",
              "artist": {"name": "Jonas Blue"}
            }
        """

        const val COVERS_FIRST_RESPONSE = """
            {
              "data": [
                {
                  "id": 9,
                  "title": "By Your Side",
                  "isrc": "COVER000001",
                  "artist": {"name": "Some Cover Band"}
                },
                {
                  "id": 2,
                  "title": "By Your Side",
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
                  "id": 3,
                  "title": "Completely Different",
                  "artist": {"name": "Jonas Blue"}
                }
              ]
            }
        """

        const val WRONG_ARTIST_RESPONSE = """
            {
              "data": [
                {
                  "id": 4,
                  "title": "By Your Side",
                  "artist": {"name": "Someone Else Entirely"}
                }
              ]
            }
        """

        const val ZERO_VALUES_TRACK_RESPONSE = """
            {
              "id": 1,
              "title": "By Your Side",
              "bpm": 0.0,
              "gain": null,
              "isrc": "",
              "artist": {"name": "Jonas Blue"}
            }
        """
    }
}
