package now.link.mastigias.data.lyrics.repository

import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.data.lyrics.remote.LrcLibClient
import now.link.mastigias.domain.model.LyricsCandidate
import now.link.mastigias.domain.repository.LyricsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LrcLibRepositoryImpl @Inject constructor(
    private val client: LrcLibClient,
    private val dispatchers: AppDispatchers
) : LyricsRepository {

    override suspend fun searchLyrics(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Result<List<LyricsCandidate>> = withContext(dispatchers.io) {
        try {
            val dtoList = client.search(trackName, artistName, albumName)
            val candidates = dtoList.mapNotNull { it.toDomain() }
            Result.success(candidates)
        } catch (e: Exception) {
            LogManager.e(TAG, "Error fetching lyrics from LRCLIB: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "LrcLibRepository"
    }
}
