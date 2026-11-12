package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.metadata.Similarity
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The handful of Deezer fields Cue keeps. Deezer publishes BPM, a ReplayGain
 * value and the ISRC on its public search endpoint; no key or account is
 * needed.
 */
data class DeezerTrack(
    val title: String,
    val artist: String,
    val bpm: Int? = null,
    val replayGainDb: Double? = null,
    val isrc: String? = null,
)

internal interface DeezerApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int,
    ): Response<DeezerSearchResponse>
}

@Serializable
internal data class DeezerSearchResponse(
    val data: List<DeezerItem> = emptyList(),
)

@Serializable
internal data class DeezerItem(
    val title: String? = null,
    val bpm: Double? = null,
    val gain: Double? = null,
    val isrc: String? = null,
    val artist: DeezerArtist? = null,
)

@Serializable
internal data class DeezerArtist(val name: String? = null)

class DeezerClient internal constructor(
    private val api: DeezerApi,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) {

    /**
     * Picks the closest search result instead of trusting the first one:
     * searching "By Your Side Jonas Blue" also returns covers and remixes, and
     * a wrong BPM is worse than no BPM.
     */
    suspend fun lookup(title: String, artist: String): LookupResult<DeezerTrack?> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(null)
        val track = title.trim()
        val performer = artist.trim()
        if (track.isEmpty()) return LookupResult.Success(null)
        return safeLookup {
            val response = api.search(
                query = listOf(track, performer).filter { it.isNotEmpty() }.joinToString(" "),
                limit = SEARCH_LIMIT,
            )
            if (!response.isSuccessful) return@safeLookup httpFailure(response.code())
            val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
            LookupResult.Success(body.data.bestMatch(track, performer))
        }
    }

    /** The value, or null for both "not found" and a typed failure. */
    suspend fun lookupOrNull(title: String, artist: String): DeezerTrack? =
        when (val result = lookup(title, artist)) {
            is LookupResult.Success -> result.value
            is LookupResult.Failure -> null
        }

    private fun List<DeezerItem>.bestMatch(title: String, artist: String): DeezerTrack? {
        val candidates = mapNotNull { item ->
            val itemTitle = item.title?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val itemArtist = item.artist?.name?.trim().orEmpty()
            val titleScore = Similarity.textSimilarity(title, itemTitle)
            if (titleScore < MIN_TITLE_SIMILARITY) return@mapNotNull null
            if (artist.isNotBlank() && itemArtist.isNotBlank() &&
                Similarity.textSimilarity(artist, itemArtist) < MIN_ARTIST_SIMILARITY
            ) {
                return@mapNotNull null
            }
            item to (titleScore + Similarity.textSimilarity(artist, itemArtist))
        }
        val best = candidates.maxByOrNull { it.second }?.first ?: return null
        return DeezerTrack(
            title = best.title.orEmpty().trim(),
            artist = best.artist?.name.orEmpty().trim(),
            bpm = best.bpm?.takeIf { it > 0 }?.let { kotlin.math.round(it).toInt() },
            replayGainDb = best.gain,
            isrc = best.isrc?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private companion object {
        const val SEARCH_LIMIT = 10
        const val MIN_TITLE_SIMILARITY = 0.75
        const val MIN_ARTIST_SIMILARITY = 0.5
    }
}
