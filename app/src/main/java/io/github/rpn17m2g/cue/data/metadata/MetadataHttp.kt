package io.github.rpn17m2g.cue.data.metadata

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal val MetadataJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

class UserAgentInterceptor(
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
            .newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}

/** Builds the single OkHttp/Retrofit stack shared by every metadata provider. */
class MetadataHttp(
    config: NetworkConfig = NetworkConfig(),
    sleeper: Sleeper = Sleeper.System,
    private val json: Json = MetadataJson,
) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor(UserAgentInterceptor(config.userAgent))
        .addInterceptor(RetryInterceptor(config.retries, config.backoffBaseMs, sleeper))
        .build()

    fun <T> api(
        baseUrl: String,
        service: Class<T>,
        client: OkHttpClient = this.client,
    ): T = retrofit(baseUrl, client).create(service)

    fun retrofit(
        baseUrl: String,
        client: OkHttpClient = this.client,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()

    fun clientWith(vararg interceptors: Interceptor): OkHttpClient =
        client.newBuilder().apply {
            interceptors.forEach { addInterceptor(it) }
        }.build()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

internal suspend fun <T> safeLookup(block: suspend () -> LookupResult<T>): LookupResult<T> = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (serialization: SerializationException) {
    LookupResult.Failure(LookupError.Parse(serialization))
} catch (io: IOException) {
    LookupResult.Failure(LookupError.Network(io))
} catch (illegal: IllegalArgumentException) {
    LookupResult.Failure(LookupError.Parse(illegal))
} catch (unexpected: RuntimeException) {
    LookupResult.Failure(LookupError.Parse(unexpected))
}
