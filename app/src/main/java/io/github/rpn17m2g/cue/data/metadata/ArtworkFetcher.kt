package io.github.rpn17m2g.cue.data.metadata

import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Downloads cover bytes from a provider-supplied HTTPS URL. */
fun interface ArtworkFetcher {
    suspend fun fetch(url: String): ByteArray?
}

class HttpArtworkFetcher(private val client: OkHttpClient) : ArtworkFetcher {

    override suspend fun fetch(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val parsed = url.toHttpUrlOrNull() ?: return@withContext null
        if (!parsed.isHttps) return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(parsed).build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_BYTES) return@use null
                readCapped(body.byteStream())
            }
        }.getOrNull()
    }

    private fun readCapped(input: InputStream): ByteArray? = input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) return@use null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val BUFFER_SIZE = 16 * 1024
    }
}
