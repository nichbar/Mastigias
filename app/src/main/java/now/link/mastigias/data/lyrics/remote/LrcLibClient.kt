package now.link.mastigias.data.lyrics.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import now.link.mastigias.BuildConfig
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.data.lyrics.model.LrcLibCandidateDto
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
open class LrcLibClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    open suspend fun search(
        trackName: String,
        artistName: String? = null,
        albumName: String? = null
    ): List<LrcLibCandidateDto> {
        val urlBuilder = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("search")
            .addQueryParameter("track_name", trackName)

        if (!artistName.isNullOrBlank()) {
            urlBuilder.addQueryParameter("artist_name", artistName)
        }
        if (!albumName.isNullOrBlank()) {
            urlBuilder.addQueryParameter("album_name", albumName)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val response = okHttpClient.executeAsync(request)
        return response.use { resp ->
            if (resp.code == 404) {
                return emptyList()
            }
            if (resp.code == 429) {
                throw IOException("LRCLIB rate limit reached (HTTP 429). Please try again in a few moments.")
            }
            if (!resp.isSuccessful) {
                throw IOException("LRCLIB request failed with HTTP ${resp.code}: ${resp.message}")
            }
            val bodyString = resp.body?.string() ?: return emptyList()
            try {
                json.decodeFromString<List<LrcLibCandidateDto>>(bodyString)
            } catch (e: Exception) {
                LogManager.e(TAG, "Failed to parse LRCLIB response: ${e.message}", e)
                throw IOException("Failed to parse lyrics response from LRCLIB", e)
            }
        }
    }

    private suspend fun OkHttpClient.executeAsync(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }

    companion object {
        private const val TAG = "LrcLibClient"
        private const val BASE_URL = "https://lrclib.net"
        val USER_AGENT = "Mastigias/${BuildConfig.VERSION_NAME} (Android; https://github.com/nowlink/mastigias)"
    }
}
