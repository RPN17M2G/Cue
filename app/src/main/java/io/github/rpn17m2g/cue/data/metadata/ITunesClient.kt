package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

internal interface ITunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("entity") entity: String,
        @Query("limit") limit: Int,
    ): Response<ITunesResponse>
}

@Serializable
internal data class ITunesResponse(
    val resultCount: Int = 0,
    val results: List<ITunesItem> = emptyList(),
)

@Serializable
internal data class ITunesItem(
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("collectionName") val collectionName: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("primaryGenreName") val primaryGenreName: String? = null,
    @SerialName("trackNumber") val trackNumber: Int? = null,
    @SerialName("artworkUrl100") val artworkUrl100: String? = null,
)

class ITunesClient internal constructor(
    private val api: ITunesApi,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) : MetadataProvider, ArtworkSearch {

    override val source: IdentificationSource = IdentificationSource.ITUNES

    override suspend fun search(query: MetadataQuery): LookupResult<List<MetadataMatch>> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(emptyList())
        return safeLookup {
            val response = api.search(
                term = query.searchTerm,
                entity = SONG_ENTITY,
                limit = SEARCH_LIMIT,
            )
            if (!response.isSuccessful) return@safeLookup httpFailure(response.code())
            val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
            LookupResult.Success(
                body.results
                    .mapNotNull { item -> item.toMatch(query) }
                    .sortedByDescending { it.score },
            )
        }
    }

    override suspend fun search(query: String): List<ArtworkCandidate> {
        if (!networkGate.isNetworkEnabled()) return emptyList()
        return try {
            val albums = searchArtwork(query, ALBUM_ENTITY)
            val candidates = if (albums.isNotEmpty()) albums else searchArtwork(query, SONG_ENTITY)
            candidates.distinctBy { it.artworkUrl600 }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    private suspend fun searchArtwork(term: String, entity: String): List<ArtworkCandidate> {
        val response = api.search(term = term, entity = entity, limit = ARTWORK_LIMIT)
        if (!response.isSuccessful) return emptyList()
        return response.body()?.results.orEmpty().mapNotNull { it.toArtworkCandidate() }
    }

    private fun ITunesItem.toMatch(query: MetadataQuery): MetadataMatch? {
        val resolvedTitle = trackName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolvedArtist = artistName?.trim().orEmpty()
        return MetadataMatch(
            title = resolvedTitle,
            artist = resolvedArtist,
            album = collectionName?.trim()?.takeIf { it.isNotEmpty() },
            year = releaseDate.toYear(),
            genre = primaryGenreName?.trim()?.takeIf { it.isNotEmpty() },
            trackNumber = trackNumber,
            artworkUrl = artworkUrl100.upscaledArtwork(),
            source = IdentificationSource.ITUNES,
            score = MetadataScorer.score(query, resolvedTitle, resolvedArtist),
        )
    }

    private fun ITunesItem.toArtworkCandidate(): ArtworkCandidate? {
        val artwork = artworkUrl100.upscaledArtwork() ?: return null
        return ArtworkCandidate(
            artworkUrl600 = artwork,
            album = collectionName?.trim()?.takeIf { it.isNotEmpty() },
            artist = artistName?.trim()?.takeIf { it.isNotEmpty() },
            title = trackName?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private companion object {
        const val SONG_ENTITY = "song"
        const val ALBUM_ENTITY = "album"
        const val SEARCH_LIMIT = 10
        const val ARTWORK_LIMIT = 25
    }
}
