package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

internal interface MusicBrainzApi {
    @GET("ws/2/recording")
    suspend fun search(
        @Query("query") query: String,
        @Query("fmt") format: String,
        @Query("limit") limit: Int,
    ): Response<MusicBrainzResponse>
}

@Serializable
internal data class MusicBrainzResponse(
    val recordings: List<MusicBrainzRecording> = emptyList(),
)

@Serializable
internal data class MusicBrainzRecording(
    val id: String? = null,
    val title: String? = null,
    val score: Int? = null,
    @SerialName("first-release-date") val firstReleaseDate: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    val releases: List<MusicBrainzRelease> = emptyList(),
)

@Serializable
internal data class MusicBrainzArtistCredit(
    val name: String? = null,
    val artist: MusicBrainzArtist? = null,
)

@Serializable
internal data class MusicBrainzArtist(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
internal data class MusicBrainzRelease(
    val id: String? = null,
    val title: String? = null,
    val date: String? = null,
)

/** Cover Art Archive lookup for a MusicBrainz release id. */
object CoverArtArchive {
    fun front500(releaseMbid: String): String =
        "https://coverartarchive.org/release/$releaseMbid/front-500"
}

class MusicBrainzClient internal constructor(
    private val api: MusicBrainzApi,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
    private val coverArtArchive: CoverArtArchiveClient? = null,
) : MetadataProvider {

    override val source: IdentificationSource = IdentificationSource.MUSICBRAINZ

    override suspend fun search(query: MetadataQuery): LookupResult<List<MetadataMatch>> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(emptyList())
        return safeLookup {
            val response = api.search(
                query = query.toLuceneQuery(),
                format = JSON_FORMAT,
                limit = SEARCH_LIMIT,
            )
            if (!response.isSuccessful) return@safeLookup httpFailure(response.code())
            val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
            val matches = body.recordings
                .mapNotNull { recording -> recording.toMappedMatch(query) }
            LookupResult.Success(enrichArtwork(matches))
        }
    }

    private suspend fun enrichArtwork(mapped: List<MappedMatch>): List<MetadataMatch> {
        val sorted = mapped.sortedByDescending { it.match.score }
        val archive = coverArtArchive ?: return sorted.map { it.match }
        val cache = mutableMapOf<String, String?>()
        return sorted
            .mapIndexed { index, candidate ->
                val releaseMbid = candidate.releaseMbid
                if (index >= MAX_ARTWORK_ENRICHED || releaseMbid.isNullOrBlank()) {
                    candidate.match
                } else {
                    val artwork = if (cache.containsKey(releaseMbid)) {
                        cache[releaseMbid]
                    } else {
                        archive.front500(releaseMbid).also { cache[releaseMbid] = it }
                    }
                    candidate.match.copy(artworkUrl = artwork)
                }
            }
    }

    private fun MusicBrainzRecording.toMappedMatch(query: MetadataQuery): MappedMatch? {
        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolvedArtist = artistCredit
            .mapNotNull { credit ->
                credit.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: credit.artist?.name?.trim()?.takeIf { it.isNotEmpty() }
            }
            .distinct()
            .joinToString(" & ")
        val release = releases.firstOrNull { !it.title.isNullOrBlank() }
        return MappedMatch(
            match = MetadataMatch(
                title = resolvedTitle,
                artist = resolvedArtist,
                album = release?.title?.trim()?.takeIf { it.isNotEmpty() },
                year = firstReleaseDate.toYear() ?: releases.firstNotNullOfOrNull { it.date.toYear() },
                source = IdentificationSource.MUSICBRAINZ,
                score = MetadataScorer.score(query, resolvedTitle, resolvedArtist),
            ),
            releaseMbid = release?.id?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private data class MappedMatch(
        val match: MetadataMatch,
        val releaseMbid: String?,
    )

    private companion object {
        const val JSON_FORMAT = "json"
        const val SEARCH_LIMIT = 10
        const val MAX_ARTWORK_ENRICHED = 3
    }
}

private val LUCENE_SPECIAL = setOf(
    '+', '-', '&', '|', '!', '(', ')', '{', '}', '[', ']',
    '^', '"', '~', '*', '?', ':', '\\', '/',
)

internal fun MetadataQuery.toLuceneQuery(): String = buildString {
    append("recording:\"")
    append(escapeLucene(title.trim()))
    append('"')
    artist?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
        append(" AND artist:\"")
        append(escapeLucene(value))
        append('"')
    }
}

internal fun escapeLucene(value: String): String = buildString {
    value.forEach { character ->
        if (character in LUCENE_SPECIAL) append('\\')
        append(character)
    }
}
