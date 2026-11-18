package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.parseLrc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Lyrics returned by LRCLIB. [text] is the plain multi-line version players
 * without synchronized-lyrics support show; [lrc] is the raw timestamped form
 * when the database has one.
 */
data class Lyrics(
    val text: String,
    val lrc: String? = null,
) {
    /** True when [lrc] carries at least one usable timestamp. */
    val synced: Boolean get() = !lrc.isNullOrBlank()
}

internal interface LrcLibApi {
    @GET("api/get")
    suspend fun get(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String? = null,
        @Query("duration") durationSeconds: Int? = null,
    ): Response<LrcLibResponse>
}

@Serializable
internal data class LrcLibResponse(
    val id: Long = 0,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("albumName") val albumName: String? = null,
    val duration: Double? = null,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
)

/**
 * LRCLIB's free lyrics API. No key is required, but the service asks clients to
 * identify themselves and to back off on 429; both are handled by the shared
 * HTTP stack and [LookupError.RateLimited].
 *
 * A track the database does not know is a normal answer, not a failure: the
 * caller still gets [LookupResult.Success] with `null` lyrics.
 */
class LrcLibClient internal constructor(
    private val api: LrcLibApi,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) {

    suspend fun fetch(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
    ): LookupResult<Lyrics?> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(null)
        val track = title.trim()
        val performer = artist.trim()
        if (track.isEmpty() || performer.isEmpty()) return LookupResult.Success(null)
        return safeLookup {
            val response = api.get(
                trackName = track,
                artistName = performer,
                albumName = album?.trim()?.takeIf { it.isNotEmpty() },
                durationSeconds = durationSeconds?.takeIf { it > 0 },
            )
            when {
                response.code() == HTTP_NOT_FOUND -> LookupResult.Success(null)
                !response.isSuccessful -> httpFailure(response.code())
                else -> {
                    val body = response.body()
                        ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
                    LookupResult.Success(body.toLyrics())
                }
            }
        }
    }

    /**
     * The value, or null for both "not found" and a typed failure. Enrichment
     * treats every failure as "nothing to add".
     */
    suspend fun fetchOrNull(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
    ): Lyrics? = when (val result = fetch(title, artist, album, durationSeconds)) {
        is LookupResult.Success -> result.value
        is LookupResult.Failure -> null
    }

    private fun LrcLibResponse.toLyrics(): Lyrics? {
        val rawSynced = syncedLyrics?.trim()?.takeIf { it.isNotEmpty() }
        val timed = rawSynced?.takeIf { parseLrc(it).isNotEmpty() }
        // A "synced" payload with no timestamps is just plain text: keep its
        // words rather than dropping the lyrics entirely.
        val plain = plainLyrics?.trim()?.takeIf { it.isNotEmpty() }
            ?: (timed ?: rawSynced)?.let(::stripTimestamps)?.takeIf { it.isNotEmpty() }
            ?: return null
        return Lyrics(plain, timed)
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}

/**
 * Turns LRC into plain text by dropping the `[mm:ss.xx]` prefix on every line
 * and metadata tags such as `[ar:...]`. Synced lyrics are only used when the
 * database has no plain version.
 */
internal fun stripTimestamps(synced: String): String = synced
    .lineSequence()
    .map { line -> line.replace(LRC_TIMESTAMP, "").trim() }
    .filter { it.isNotEmpty() && !LRC_METADATA.matches(it) }
    .joinToString("\n")

private val LRC_TIMESTAMP = Regex("""\[\d{1,2}:\d{2}(?:[.:]\d{1,3})?]""")
private val LRC_METADATA = Regex("""^\[[a-zA-Z]+:.*]$""")
