package now.link.mastigias.domain.repository

import now.link.mastigias.domain.model.LyricsCandidate

interface LyricsRepository {
    suspend fun searchLyrics(
        trackName: String,
        artistName: String? = null,
        albumName: String? = null
    ): Result<List<LyricsCandidate>>
}
