package io.github.rpn17m2g.cue.data.metadata

import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

internal interface CoverArtArchiveApi {
    @GET("release/{mbid}/front-500")
    suspend fun front500(@Path("mbid") releaseMbid: String): Response<ResponseBody>
}

internal class CoverArtArchiveClient(
    private val api: CoverArtArchiveApi,
    private val networkGate: NetworkGate = NetworkGate.Enabled,
) {
    suspend fun front500(releaseMbid: String): String? {
        if (releaseMbid.isBlank() || !networkGate.isNetworkEnabled()) return null
        return try {
            val response = api.front500(releaseMbid)
            response.body()?.close()
            response.errorBody()?.close()
            if (response.isSuccessful) CoverArtArchive.front500(releaseMbid) else null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            null
        }
    }
}
