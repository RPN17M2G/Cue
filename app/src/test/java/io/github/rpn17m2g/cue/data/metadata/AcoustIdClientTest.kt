package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import java.net.URLDecoder
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AcoustIdClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AcoustIdClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val http = MetadataHttp(
            config = NetworkConfig(retries = 0),
            sleeper = Sleeper.None,
        )
        client = AcoustIdClient(
            api = http.api(server.url("/").toString(), AcoustIdApi::class.java),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `blank key fails fast without touching the network`() = runTest {
        val result = client.lookup(FingerprintInput(fingerprint = "chromaprint", durationSeconds = 30))

        assertEquals(LookupError.MissingKey, (result as LookupResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `blank explicit key fails fast without touching the network`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        val result = client.lookup(
            input = FingerprintInput(fingerprint = "chromaprint", durationSeconds = 30),
            apiKey = "   ",
        )

        assertEquals(LookupError.MissingKey, (result as LookupResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `maps the best recording from a lookup`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        val result = client.lookup(
            input = FingerprintInput(fingerprint = "AQADtEmi", durationSeconds = 30),
            apiKey = "test-key",
        )

        val matches = (result as LookupResult.Success).value
        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("Imagine", match.title)
        assertEquals("John Lennon", match.artist)
        assertEquals("Imagine", match.album)
        assertEquals(IdentificationSource.FINGERPRINT, match.source)
        assertEquals(0.98, match.score, 0.0001)
    }

    @Test
    fun `maps all recordings and deduplicates by title and artist`() = runTest {
        server.enqueue(jsonResponse(ALTERNATIVES_RESPONSE))

        val matches = (
            client.lookup(
                input = FingerprintInput("chromaprint", 30),
                apiKey = "test-key",
            ) as LookupResult.Success
            ).value

        assertEquals(2, matches.size)
        assertEquals(setOf("Imagine", "Imagine (Remastered)"), matches.map { it.title }.toSet())
        assertEquals(1, matches.count { it.title == "Imagine" })
    }

    @Test
    fun `posts form fields with the api key and fingerprint`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        client.lookup(
            input = FingerprintInput(fingerprint = "AQADtEmi", durationSeconds = 30),
            apiKey = "test-key",
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/lookup", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("client=test-key"))
        assertTrue(body.contains("duration=30"))
        assertTrue(body.contains("fingerprint=AQADtEmi"))
        assertTrue(body.contains("meta=recordings+releasegroups"))
    }

    @Test
    fun `meta field decodes to space separated tokens`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "test-key",
        )

        val body = server.takeRequest().body.readUtf8()
        val rawMeta = body.split("&").first { it.startsWith("meta=") }.substringAfter("meta=")
        assertEquals("recordings releasegroups", URLDecoder.decode(rawMeta, "UTF-8"))
    }

    @Test
    fun `invalid key response maps to missing key`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"status":"error","error":{"code":4,"message":"invalid API key"}}""",
            ),
        )

        val result = client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "bad-key",
        )

        assertEquals(LookupError.MissingKey, (result as LookupResult.Failure).error)
    }

    @Test
    fun `http 400 invalid key body maps to missing key`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"error","error":{"code":4,"message":"invalid API key"}}"""),
        )

        val result = client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "bad-key",
        )

        assertEquals(LookupError.MissingKey, (result as LookupResult.Failure).error)
    }

    @Test
    fun `http 429 error body with rate limit code maps to rate limited`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"error","error":{"code":14,"message":"rate limited"}}"""),
        )

        val result = client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "test-key",
        )

        assertEquals(LookupError.RateLimited, (result as LookupResult.Failure).error)
    }

    @Test
    fun `http 503 error body with service unavailable code maps to service unavailable`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"error","error":{"code":13,"message":"service unavailable"}}"""),
        )

        val result = client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "test-key",
        )

        assertEquals(LookupError.Http(503), (result as LookupResult.Failure).error)
    }

    @Test
    fun `malformed json becomes a parse error`() = runTest {
        server.enqueue(jsonResponse("<html>nope</html>"))

        val result = client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "test-key",
        )

        assertTrue((result as LookupResult.Failure).error is LookupError.Parse)
    }

    @Test
    fun `http 429 without a body becomes rate limited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = client.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "test-key",
        )

        assertEquals(LookupError.RateLimited, (result as LookupResult.Failure).error)
    }

    @Test
    fun `post is not replayed even when retries are configured`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val sleeps = mutableListOf<Long>()
        val retrying = AcoustIdClient(
            api = MetadataHttp(
                config = NetworkConfig(retries = 2),
                sleeper = Sleeper { millis -> sleeps += millis },
            ).api(server.url("/").toString(), AcoustIdApi::class.java),
        )

        val result = retrying.lookup(
            input = FingerprintInput("chromaprint", 30),
            apiKey = "test-key",
        )

        assertEquals(LookupError.RateLimited, (result as LookupResult.Failure).error)
        assertEquals(1, server.requestCount)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `network gate blocks requests even with a key`() = runTest {
        val gated = AcoustIdClient(
            api = MetadataHttp(sleeper = Sleeper.None).api(
                server.url("/").toString(),
                AcoustIdApi::class.java,
            ),
            defaultApiKey = "test-key",
            networkGate = NetworkGate.Disabled,
        )

        val result = gated.lookup(FingerprintInput("chromaprint", 30))

        assertEquals(emptyList<Any>(), (result as LookupResult.Success).value)
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val RESPONSE = """
            {
              "status": "ok",
              "results": [
                {
                  "id": "result-1",
                  "score": 0.98,
                  "recordings": [
                    {
                      "id": "recording-1",
                      "title": "Imagine",
                      "artists": [{"id": "artist-1", "name": "John Lennon"}],
                      "releasegroups": [{"id": "rg-1", "title": "Imagine"}]
                    }
                  ]
                }
              ]
            }
        """

        const val ALTERNATIVES_RESPONSE = """
            {
              "status": "ok",
              "results": [
                {
                  "id": "result-1",
                  "score": 0.9,
                  "recordings": [
                    {
                      "id": "recording-1",
                      "title": "Imagine",
                      "artists": [{"id": "artist-1", "name": "John Lennon"}],
                      "releasegroups": [{"id": "rg-1", "title": "Imagine"}]
                    },
                    {
                      "id": "recording-2",
                      "title": "Imagine (Remastered)",
                      "artists": [{"id": "artist-1", "name": "John Lennon"}]
                    }
                  ]
                },
                {
                  "id": "result-2",
                  "score": 0.8,
                  "recordings": [
                    {
                      "id": "recording-3",
                      "title": "Imagine",
                      "artists": [{"id": "artist-1", "name": "John Lennon"}]
                    }
                  ]
                }
              ]
            }
        """
    }
}
