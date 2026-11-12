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

class LrcLibClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: LrcLibClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = LrcLibClient(
            api = MetadataHttp(
                config = NetworkConfig(retries = 0),
                sleeper = Sleeper.None,
            ).api(server.url("/").toString(), LrcLibApi::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns plain lyrics when the database has them`() = runTest {
        server.enqueue(jsonResponse(PLAIN_RESPONSE))

        val lyrics = client.fetch("By Your Side", "Jonas Blue").success()

        assertEquals("I want to be by your side\nEvery day and every night", lyrics?.text)
        assertEquals(false, lyrics?.synced)
    }

    @Test
    fun `falls back to synced lyrics with the timestamps stripped`() = runTest {
        server.enqueue(jsonResponse(SYNCED_RESPONSE))

        val lyrics = client.fetch("By Your Side", "Jonas Blue").success()

        assertEquals("First line\nSecond line", lyrics?.text)
        assertEquals(false, lyrics?.synced)
    }

    @Test
    fun `prefers plain lyrics over the synced version`() = runTest {
        server.enqueue(jsonResponse(BOTH_RESPONSE))

        val lyrics = client.fetch("Song", "Artist").success()

        assertEquals("Plain words", lyrics?.text)
    }

    @Test
    fun `sends title artist album and duration`() = runTest {
        server.enqueue(jsonResponse(PLAIN_RESPONSE))

        client.fetch("By Your Side", "Jonas Blue", album = "Blue", durationSeconds = 204)

        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/get", url.encodedPath)
        assertEquals("By Your Side", url.queryParameter("track_name"))
        assertEquals("Jonas Blue", url.queryParameter("artist_name"))
        assertEquals("Blue", url.queryParameter("album_name"))
        assertEquals("204", url.queryParameter("duration"))
    }

    @Test
    fun `omits an empty album and a non-positive duration`() = runTest {
        server.enqueue(jsonResponse(PLAIN_RESPONSE))

        client.fetch("Song", "Artist", album = "  ", durationSeconds = 0)

        val url = server.takeRequest().requestUrl!!
        assertNull(url.queryParameter("album_name"))
        assertNull(url.queryParameter("duration"))
    }

    @Test
    fun `a track the database does not know is a successful empty answer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"code":404}"""))

        val result = client.fetch("Unknown", "Nobody")

        assertTrue(result is LookupResult.Success)
        assertNull((result as LookupResult.Success).value)
    }

    @Test
    fun `an http failure is surfaced as a typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client.fetch("Song", "Artist")

        assertEquals(LookupError.Http(503), (result as LookupResult.Failure).error)
    }

    @Test
    fun `a blank title never touches the network`() = runTest {
        val result = client.fetch("   ", "Artist")

        assertNull((result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a blank artist never touches the network`() = runTest {
        val result = client.fetch("Song", "")

        assertNull((result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a closed network gate never calls the provider`() = runTest {
        val gated = LrcLibClient(
            api = MetadataHttp(sleeper = Sleeper.None)
                .api(server.url("/").toString(), LrcLibApi::class.java),
            networkGate = NetworkGate.Disabled,
        )

        val result = gated.fetch("Song", "Artist")

        assertNull((result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `fetchOrNull collapses a failure to nothing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertNull(client.fetchOrNull("Song", "Artist"))
    }

    @Test
    fun `strip timestamps drops lrc metadata tags and blank lines`() {
        val synced = """
            [ar:Jonas Blue]
            [ti:By Your Side]
            [00:12.34]First line
            [00:15.00]Second line

            [00:18.50]Third line
        """.trimIndent()

        assertEquals("First line\nSecond line\nThird line", stripTimestamps(synced))
    }

    private fun LookupResult<Lyrics?>.success(): Lyrics? = (this as LookupResult.Success).value

    private companion object {
        const val PLAIN_RESPONSE = """
            {
              "id": 1,
              "trackName": "By Your Side",
              "artistName": "Jonas Blue",
              "albumName": "Blue",
              "duration": 204,
              "plainLyrics": "I want to be by your side\nEvery day and every night",
              "syncedLyrics": "[00:01.00]I want to be by your side"
            }
        """

        const val SYNCED_RESPONSE = """
            {
              "id": 2,
              "trackName": "By Your Side",
              "artistName": "Jonas Blue",
              "plainLyrics": null,
              "syncedLyrics": "[00:12.34]First line\n[00:15.00]Second line"
            }
        """

        const val BOTH_RESPONSE = """
            {
              "id": 3,
              "trackName": "Song",
              "artistName": "Artist",
              "plainLyrics": "Plain words",
              "syncedLyrics": "[00:01.00]Synced words"
            }
        """
    }
}
