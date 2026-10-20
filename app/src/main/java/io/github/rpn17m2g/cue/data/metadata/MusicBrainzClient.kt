package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface MusicBrainzApi {
    @GET("ws/2/recording")
    suspend fun search(
        @Query("query") query: String,
        @Query("fmt") format: String,
        @Query("limit") limit: Int,
    ): Response<MusicBrainzResponse>
}

@Serializable
data class MusicBrainzResponse(
    val recordings: List<MusicBrainzRecording> = emptyList(),
)

@Serializable
data class MusicBrainzRecording(
    val id: String? = null,
    val title: String? = null,
    val score: Int? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    val releases: List<MusicBrainzRelease> = emptyList(),
)

@Serializable
data class MusicBrainzArtistCredit(
    val name: String? = null,
    val artist: MusicBrainzArtist? = null,
)

@Serializable
data class MusicBrainzArtist(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class MusicBrainzRelease(
    val id: String? = null,
    val title: String? = null,
    val date: String? = null,
)

/** Cover Art Archive lookup for a MusicBrainz release id. */
object CoverArtArchive {
    fun front500(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-500"
}

class MusicBrainzClient(
    private val api: MusicBrainzApi,
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) : MetadataProvider {

    override val source: IdentificationSource = IdentificationSource.MUSICBRAINZ

    override suspend fun search(query: MetadataQuery): LookupResult<List<MetadataMatch>> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(emptyList())
        rateLimiter.acquire()
        return safeLookup {
            val response = api.search(
                query = query.searchTerm,
                format = JSON_FORMAT,
                limit = SEARCH_LIMIT,
            )
            if (!response.isSuccessful) return@safeLookup httpFailure(response.code())
            val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
            LookupResult.Success(
                body.recordings
                    .mapNotNull { recording -> recording.toMatch(query) }
                    .sortedByDescending { it.score },
            )
        }
    }

    private fun MusicBrainzRecording.toMatch(query: MetadataQuery): MetadataMatch? {
        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolvedArtist = artistCredit
            .mapNotNull { credit ->
                credit.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: credit.artist?.name?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinct()
            .joinToString(" & ")
        val release = releases.firstOrNull { !it.title.isNullOrBlank() }
        return MetadataMatch(
            title = resolvedTitle,
            artist = resolvedArtist,
            album = release?.title?.trim()?.takeIf { it.isNotEmpty() },
            year = firstReleaseDate.toYear() ?: releases.firstNotNullOfOrNull { it.date.toYear() },
            source = IdentificationSource.MUSICBRAINZ,
            score = MetadataScorer.score(query, resolvedTitle, resolvedArtist),
        )
    }

    private companion object {
        const val JSON_FORMAT = "json"
        const val SEARCH_LIMIT = 10
    }
}
