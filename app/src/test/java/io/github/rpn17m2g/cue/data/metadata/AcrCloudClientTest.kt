package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AcrCloudClientTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var sample: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        sample = tempFolder.newFile("sample.mp3").apply { writeBytes(ByteArray(2048) { it.toByte() }) }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `maps the best music result to a match`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        val matches = client().identify(sample).success()

        assertEquals(1, matches.size)
        val match = matches.single()
        assertEquals("By Your Side", match.title)
        assertEquals("Jonas Blue & Raye", match.artist)
        assertEquals("Blue", match.album)
        assertEquals("2017", match.year)
        assertEquals("Dance", match.genre)
        assertEquals("GBUM71700001", match.isrc)
        assertEquals("Positiva", match.label)
        assertEquals("recording-mbid", match.recordingMbid)
        assertEquals(IdentificationSource.ACRCLOUD, match.source)
        assertEquals(0.97, match.score, 0.0001)
    }

    @Test
    fun `a known request with no result is a successful empty list`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":1001,"msg":"No result"},"metadata":null}"""))

        val matches = client().identify(sample).success()

        assertEquals(emptyList<Any>(), matches)
    }

    @Test
    fun `bad credentials map to a rejected key`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":3001,"msg":"Invalid Access Key"}}"""))

        val result = client().identify(sample)

        assertEquals(LookupError.RejectedKey, (result as LookupResult.Failure).error)
    }

    @Test
    fun `quota errors are not reported as bad credentials`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":3014,"msg":"Quota exceeded"}}"""))

        val result = client().identify(sample)

        assertEquals(LookupError.Http(3014), (result as LookupResult.Failure).error)
    }

    @Test
    fun `sends the signed multipart form`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        client().identify(sample)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/identify", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"sample\""))
        assertTrue(body.contains("name=\"sample_bytes\""))
        assertTrue(body.contains("2048"))
        assertTrue(body.contains("name=\"access_key\""))
        assertTrue(body.contains("ACCESS_KEY"))
        assertTrue(body.contains("name=\"data_type\""))
        assertTrue(body.contains("audio"))
        assertTrue(body.contains("name=\"signature_version\""))
        assertTrue(body.contains("name=\"timestamp\""))
        assertTrue(body.contains("1700000000"))
        assertTrue(body.contains("aaepYYfreKJFpHHlwJqAxEshcKA="))
    }

    @Test
    fun `an empty sample fails without touching the network`() = runTest {
        val empty = tempFolder.newFile("empty.mp3")

        val result = client().identify(empty)

        assertEquals(LookupError.Parse(), (result as LookupResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a missing sample fails without touching the network`() = runTest {
        val result = client().identify(File(tempFolder.root, "missing.mp3"))

        assertEquals(LookupError.Parse(), (result as LookupResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a closed network gate never calls the provider`() = runTest {
        val result = client(networkGate = NetworkGate.Disabled).identify(sample)

        assertEquals(emptyList<Any>(), result.success())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an http failure maps to a typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client().identify(sample)

        assertEquals(LookupError.Http(503), (result as LookupResult.Failure).error)
    }

    @Test
    fun `check credentials succeeds on a successful identify`() = runTest {
        server.enqueue(jsonResponse(RESPONSE))

        val result = client().checkCredentials()

        assertTrue(result is LookupResult.Success)
    }

    @Test
    fun `check credentials treats no result as valid credentials`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":1001,"msg":"No result"}}"""))

        val result = client().checkCredentials()

        assertTrue(result is LookupResult.Success)
    }

    @Test
    fun `check credentials treats a sample complaint as valid credentials`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":1004,"msg":"Invalid sample"}}"""))

        val result = client().checkCredentials()

        assertTrue(result is LookupResult.Success)
    }

    @Test
    fun `check credentials reports rejected credentials`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":3001,"msg":"Invalid Access Key"}}"""))

        val result = client().checkCredentials()

        assertEquals(LookupError.RejectedKey, (result as LookupResult.Failure).error)
    }

    @Test
    fun `check credentials reports other errors`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":3014,"msg":"Quota exceeded"}}"""))

        val result = client().checkCredentials()

        assertEquals(LookupError.Http(3014), (result as LookupResult.Failure).error)
    }

    @Test
    fun `check credentials refuses to run offline`() = runTest {
        val result = client(networkGate = NetworkGate.Disabled).checkCredentials()

        assertEquals(LookupError.Network(), (result as LookupResult.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `check credentials uploads the silent probe with its real size`() = runTest {
        server.enqueue(jsonResponse("""{"status":{"code":1001,"msg":"No result"}}"""))

        client().checkCredentials()

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("filename=\"cue-probe.mp3\""))
        assertTrue(body.contains("name=\"sample_bytes\""))
        assertTrue(body.contains("20016"))
    }

    private fun client(networkGate: NetworkGate = NetworkGate.Enabled): AcrCloudClient {
        val http = MetadataHttp(
            config = NetworkConfig(retries = 0),
            sleeper = Sleeper.None,
        )
        return AcrCloudClient(
            api = http.api(server.url("/").toString(), AcrCloudApi::class.java),
            accessKey = "ACCESS_KEY",
            accessSecret = "SECRET",
            networkGate = networkGate,
            nowSeconds = { 1_700_000_000L },
        )
    }

    private fun LookupResult<List<MetadataMatch>>.success(): List<MetadataMatch> =
        (this as LookupResult.Success).value

    private companion object {
        const val RESPONSE = """
            {
              "status": {"code": 0, "msg": "Success"},
              "metadata": {
                "music": [
                  {
                    "title": "By Your Side",
                    "artists": [{"name": "Jonas Blue"}, {"name": "Raye"}],
                    "album": {"name": "Blue"},
                    "release_date": "2017-06-16",
                    "genres": [{"name": "Dance"}],
                    "label": "Positiva",
                    "score": 97,
                    "external_ids": {"isrc": "GBUM71700001"},
                    "external_metadata": {
                      "musicbrainz": {"track": {"id": "recording-mbid"}}
                    }
                  }
                ]
              }
            }
        """
    }
}
