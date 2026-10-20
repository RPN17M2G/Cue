package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AcoustIdApi {
    @FormUrlEncoded
    @POST("v2/lookup")
    suspend fun lookup(
        @Field("client") client: String,
        @Field("duration") durationSeconds: Int,
        @Field("fingerprint") fingerprint: String,
        @Field("meta") meta: String,
    ): Response<AcoustIdResponse>
}

@Serializable
data class AcoustIdResponse(
    val status: String = "",
    val error: AcoustIdError? = null,
    val results: List<AcoustIdResult> = emptyList(),
)

@Serializable
data class AcoustIdError(
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
data class AcoustIdResult(
    val id: String? = null,
    val score: Double? = null,
    val recordings: List<AcoustIdRecording> = emptyList(),
)

@Serializable
data class AcoustIdRecording(
    val id: String? = null,
    val title: String? = null,
    val artists: List<AcoustIdArtist> = emptyList(),
    val releasegroups: List<AcoustIdReleaseGroup> = emptyList(),
)

@Serializable
data class AcoustIdArtist(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class AcoustIdReleaseGroup(
    val id: String? = null,
    val title: String? = null,
)

class AcoustIdClient(
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
                meta = META,
            )
            if (!response.isSuccessful) return@safeLookup httpFailure(response.code())
            val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
            if (!body.status.equals(STATUS_OK, ignoreCase = true)) {
                return@safeLookup LookupResult.Failure(body.error.toLookupError())
            }
            LookupResult.Success(body.toMatches())
        }
    }

    private fun AcoustIdResponse.toMatches(): List<MetadataMatch> = results
        .sortedByDescending { it.score ?: 0.0 }
        .mapNotNull { result ->
            val recording = result.recordings.firstOrNull() ?: return@mapNotNull null
            val resolvedTitle = recording.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val resolvedArtist = recording.artists
                .mapNotNull { it.name?.trim()?.takeIf { name -> name.isNotEmpty() } }
                .joinToString(", ")
            MetadataMatch(
                title = resolvedTitle,
                artist = resolvedArtist,
                album = recording.releasegroups.firstOrNull()
                    ?.title
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                source = IdentificationSource.FINGERPRINT,
                score = (result.score ?: 0.0).coerceIn(0.0, 1.0),
            )
        }
        .distinctBy { match -> MetadataScorer.normalize(match.title) to MetadataScorer.normalize(match.artist) }

    private fun AcoustIdError?.toLookupError(): LookupError {
        if (this == null) return LookupError.Parse()
        val keyProblem = code == INVALID_API_KEY ||
            message?.contains("key", ignoreCase = true) == true
        return if (keyProblem) LookupError.MissingKey else LookupError.Parse()
    }

    private companion object {
        const val META = "recordings+releasegroups"
        const val STATUS_OK = "ok"
        const val INVALID_API_KEY = 4
    }
}
