package io.github.rpn17m2g.cue.data.metadata

import io.github.rpn17m2g.cue.domain.model.IdentificationSource
import io.github.rpn17m2g.cue.domain.model.MetadataMatch
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * ACRCloud's Identify Protocol v1.
 *
 * Unlike AcoustID, ACRCloud matches a fragment taken from anywhere in the song,
 * so a recording that starts mid-track is still identified. The request is
 * signed with HMAC-SHA1 over method, path, access key, data type, signature
 * version and timestamp; the secret itself never leaves the device.
 */
internal interface AcrCloudApi {
    @Multipart
    @POST("v1/identify")
    suspend fun identify(
        @Part sample: MultipartBody.Part,
        @Part("sample_bytes") sampleBytes: RequestBody,
        @Part("access_key") accessKey: RequestBody,
        @Part("data_type") dataType: RequestBody,
        @Part("signature_version") signatureVersion: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part("signature") signature: RequestBody,
    ): Response<AcrCloudResponse>
}

@Serializable
internal data class AcrCloudResponse(
    val status: AcrCloudStatus = AcrCloudStatus(),
    val metadata: AcrCloudMetadata? = null,
)

@Serializable
internal data class AcrCloudStatus(
    val code: Int = -1,
    val msg: String = "",
)

@Serializable
internal data class AcrCloudMetadata(
    val music: List<AcrCloudMusic> = emptyList(),
)

@Serializable
internal data class AcrCloudMusic(
    val title: String? = null,
    val artists: List<AcrCloudArtist> = emptyList(),
    val album: AcrCloudAlbum? = null,
    val score: Int? = null,
    val label: String? = null,
    val genres: List<AcrCloudGenre> = emptyList(),
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("play_offset_ms") val playOffsetMs: Long? = null,
    @SerialName("external_ids") val externalIds: AcrCloudExternalIds? = null,
    @SerialName("external_metadata") val externalMetadata: AcrCloudExternalMetadata? = null,
)

@Serializable
internal data class AcrCloudArtist(val name: String? = null)

@Serializable
internal data class AcrCloudAlbum(val name: String? = null)

@Serializable
internal data class AcrCloudGenre(val name: String? = null)

@Serializable
internal data class AcrCloudExternalIds(val isrc: String? = null)

@Serializable
internal data class AcrCloudExternalMetadata(
    val musicbrainz: AcrCloudMusicBrainz? = null,
)

@Serializable
internal data class AcrCloudMusicBrainz(val track: AcrCloudId? = null)

@Serializable
internal data class AcrCloudId(val id: String? = null)

/** HMAC-SHA1 signature required by the Identify Protocol. */
object AcrCloudSignature {

    private const val METHOD = "POST"
    private const val PATH = "/v1/identify"
    private const val DATA_TYPE = "audio"
    private const val SIGNATURE_VERSION = "1"

    fun sign(accessKey: String, accessSecret: String, timestamp: String): String {
        val toSign = listOf(
            METHOD,
            PATH,
            accessKey,
            DATA_TYPE,
            SIGNATURE_VERSION,
            timestamp,
        ).joinToString("\n")
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(accessSecret.toByteArray(StandardCharsets.US_ASCII), "HmacSHA1"))
        val digest = mac.doFinal(toSign.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }
}

class AcrCloudClient internal constructor(
    private val api: AcrCloudApi,
    private val accessKey: String,
    private val accessSecret: String,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {

    /**
     * Identifies the audio in [sample]. A valid request with no known song is a
     * successful empty list; bad credentials come back as [LookupError.RejectedKey]
     * so Settings can tell the user what is actually wrong.
     */
    suspend fun identify(sample: File): LookupResult<List<MetadataMatch>> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Success(emptyList())
        if (!sample.isFile || sample.length() == 0L) {
            return LookupResult.Failure(LookupError.Parse())
        }
        return when (
            val result = request(
                sampleName = sample.name,
                sampleBody = sample.asRequestBody(AUDIO_MEDIA),
                sampleBytes = sample.length(),
            )
        ) {
            is LookupResult.Success -> LookupResult.Success(
                result.value.metadata?.music.orEmpty()
                    .mapNotNull { music -> music.toMatch() }
                    .sortedByDescending { it.score },
            )
            is LookupResult.Failure -> result
        }
    }

    /**
     * Proves the three project values reach ACRCloud. The probe is a short
     * silent MP3: a wrong key or signature is rejected before the audio is
     * examined, so any non-auth business answer means the credentials work.
     * A silent probe cannot match a song, which is why a "no result" answer is
     * the expected success.
     */
    suspend fun checkCredentials(): LookupResult<Unit> {
        if (!networkGate.isNetworkEnabled()) return LookupResult.Failure(LookupError.Network())
        val audio = AcrCloudProbe.silentMp3()
        return when (
            val result = request(
                sampleName = PROBE_NAME,
                sampleBody = audio.toRequestBody(AUDIO_MEDIA),
                sampleBytes = audio.size.toLong(),
            )
        ) {
            is LookupResult.Success -> LookupResult.Success(Unit)
            is LookupResult.Failure -> when (val error = result.error) {
                // A sample/format complaint still means the request was signed
                // and accepted by the project.
                is LookupError.Http -> if (error.code in BUSINESS_ERRORS) {
                    LookupResult.Success(Unit)
                } else {
                    result
                }
                else -> result
            }
        }
    }

    private suspend fun request(
        sampleName: String,
        sampleBody: RequestBody,
        sampleBytes: Long,
    ): LookupResult<AcrCloudResponse> = safeLookup {
        val timestamp = nowSeconds().toString()
        val response = api.identify(
            sample = MultipartBody.Part.createFormData("sample", sampleName, sampleBody),
            sampleBytes = sampleBytes.toString().toBody(),
            accessKey = accessKey.toBody(),
            dataType = DATA_TYPE.toBody(),
            signatureVersion = SIGNATURE_VERSION.toBody(),
            timestamp = timestamp.toBody(),
            signature = AcrCloudSignature.sign(accessKey, accessSecret, timestamp).toBody(),
        )
        if (!response.isSuccessful) return@safeLookup httpFailure(response.code())
        val body = response.body() ?: return@safeLookup LookupResult.Failure(LookupError.Parse())
        when {
            body.status.code == SUCCESS || body.status.code == NO_RESULT -> LookupResult.Success(body)
            body.status.code in AUTH_ERRORS -> LookupResult.Failure(LookupError.RejectedKey)
            else -> LookupResult.Failure(LookupError.Http(body.status.code))
        }
    }

    private fun AcrCloudMusic.toMatch(): MetadataMatch? {
        val resolvedTitle = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val resolvedArtist = artists
            .mapNotNull { artist -> artist.name?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
            .joinToString(" & ")
        return MetadataMatch(
            title = resolvedTitle,
            artist = resolvedArtist,
            album = album?.name?.trim()?.takeIf { it.isNotEmpty() },
            year = releaseDate.toYear(),
            genre = genres.firstOrNull()?.name?.trim()?.takeIf { it.isNotEmpty() },
            source = IdentificationSource.ACRCLOUD,
            // ACRCloud scores 70–100; the cascade compares everything on 0–1.
            score = ((score ?: 0).coerceIn(0, 100)) / 100.0,
            isrc = externalIds?.isrc?.trim()?.takeIf { it.isNotEmpty() },
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            recordingMbid = externalMetadata?.musicbrainz?.track?.id
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
        )
    }

    private fun String.toBody(): RequestBody = toRequestBody(TEXT_MEDIA)

    private companion object {
        const val SUCCESS = 0
        const val NO_RESULT = 1001
        const val DATA_TYPE = "audio"
        const val SIGNATURE_VERSION = "1"
        const val PROBE_NAME = "cue-probe.mp3"

        /** 3000–3010: the project credentials or quota, not the audio, are wrong. */
        val AUTH_ERRORS = setOf(2003, 3000, 3001, 3002, 3004, 3010)

        /** 1002–1005: the signed request reached the project but the sample was unusable. */
        val BUSINESS_ERRORS = 1002..1005

        val AUDIO_MEDIA = "audio/mpeg".toMediaType()
        val TEXT_MEDIA = "text/plain".toMediaType()
    }
}

/**
 * A tiny silent MP3 used only to check credentials. MPEG-1 Layer III at
 * 128 kbps / 44.1 kHz has 417-byte frames; a header plus zeroed payload is
 * valid silence, so ACRCloud answers "no result" instead of rejecting the
 * request before it looks at the key.
 */
internal object AcrCloudProbe {

    private const val FRAME_LENGTH = 417
    private const val FRAMES = 48

    fun silentMp3(): ByteArray {
        val frame = ByteArray(FRAME_LENGTH)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00
        return ByteArray(FRAMES * FRAME_LENGTH).also { audio ->
            repeat(FRAMES) { index ->
                frame.copyInto(audio, index * FRAME_LENGTH)
            }
        }
    }
}
