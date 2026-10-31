package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

internal interface AcoustIdApi {
    @FormUrlEncoded
    @POST("v2/lookup")
    suspend fun lookup(
        @Field("client") client: String,
        @Field("duration") durationSeconds: Int,
        @Field("fingerprint") fingerprint: String,
        @Field("meta", encoded = true) meta: String,
    ): Response<AcoustIdResponse>
}

@Serializable
internal data class AcoustIdResponse(
    val status: String = "",
    val error: AcoustIdError? = null,
    val results: List<AcoustIdResult> = emptyList(),
)

@Serializable
internal data class AcoustIdError(
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class AcoustIdResult(
    val id: String? = null,
    val score: Double? = null,
    val recordings: List<AcoustIdRecording> = emptyList(),
)

@Serializable
internal data class AcoustIdRecording(
    val id: String? = null,
    val title: String? = null,
    val artists: List<AcoustIdArtist> = emptyList(),
    val releasegroups: List<AcoustIdReleaseGroup> = emptyList(),
)

@Serializable
internal data class AcoustIdArtist(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
internal data class AcoustIdReleaseGroup(
    val id: String? = null,
    val title: String? = null,
)

class AcoustIdClient internal constructor(
    private val api: AcoustIdApi,
    private val defaultApiKey: String = "",
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) : FingerprintLookup {

    override suspend fun lookup(input: FingerprintInput): LookupResult<List<MetadataMatch>> =
        lookup(input, defaultApiKey)

    suspend fun lookup(
        input: FingerprintInput,
        apiKey: String,
    ): LookupResult<List<MetadataMatch>> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(emptyList())
        if (apiKey.isBlank()) return LookupResult.Failure(LookupError.MissingKey)
        return safeLookup {
            val response = api.lookup(
                client = apiKey,
                durationSeconds = input.durationSeconds,
                fingerprint = input.fingerprint,
                meta = META_FORM_ENCODED,
            )
            if (!response.isSuccessful) {
                return@safeLookup LookupResult.Failure(
                    parseErrorBody(response).toLookupError(response.code()),
                )
            }
            val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
            if (!body.status.equals(STATUS_OK, ignoreCase = true)) {
                return@safeLookup LookupResult.Failure(body.error.toLookupError(httpCode = null))
            }
            LookupResult.Success(body.toMatches())
        }
    }

    private fun parseErrorBody(response: Response<AcoustIdResponse>): AcoustIdError? {
        val raw = response.errorBody()?.string() ?: return null
        return try {
            MetadataJson.decodeFromString(AcoustIdResponse.serializer(), raw).error
        } catch (ignored: Exception) {
            null
        }
    }

    private fun AcoustIdResponse.toMatches(): List<MetadataMatch> = results
        .sortedByDescending { it.score ?: 0.0 }
        .flatMap { result ->
            result.recordings.mapNotNull { recording -> recording.toMatch(result.score) }
        }
        .distinctBy { match ->
            MetadataScorer.normalize(match.title) to MetadataScorer.normalize(match.artist)
        }

    private fun AcoustIdRecording.toMatch(score: Double?): MetadataMatch? {
        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolvedArtist = artists
            .mapNotNull { it.name?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .joinToString(", ")
        return MetadataMatch(
            title = resolvedTitle,
            artist = resolvedArtist,
            album = releasegroups.firstOrNull()?.title?.trim()?.takeIf { it.isNotEmpty() },
            source = IdentificationSource.FINGERPRINT,
            score = (score ?: 0.0).coerceIn(0.0, 1.0),
        )
    }

    private fun AcoustIdError?.toLookupError(httpCode: Int?): LookupError = when (this?.code) {
        // The service rejects unregistered keys (and revoked ones) this way. That
        // is not the same as having no key at all: the user did enter one, so the
        // UI has to say it was refused instead of asking for a key again.
        INVALID_API_KEY, INVALID_API_KEY_ALT -> LookupError.RejectedKey
        TOO_MANY_REQUESTS -> LookupError.RateLimited
        SERVICE_UNAVAILABLE -> LookupError.Http(HTTP_SERVICE_UNAVAILABLE)
        else -> when {
            httpCode == null -> LookupError.Parse()
            httpCode == 429 -> LookupError.RateLimited
            else -> LookupError.Http(httpCode)
        }
    }

    private companion object {
        // AcoustID splits meta on whitespace; send the form encoding explicitly
        // because OkHttp encodes a space as %20, which some proxies mangle.
        const val META_FORM_ENCODED = "recordings+releasegroups"
        const val STATUS_OK = "ok"
        const val INVALID_API_KEY = 4
        const val INVALID_API_KEY_ALT = 17
        const val SERVICE_UNAVAILABLE = 13
        const val TOO_MANY_REQUESTS = 14
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}
