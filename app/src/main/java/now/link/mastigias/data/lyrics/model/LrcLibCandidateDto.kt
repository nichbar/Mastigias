package now.link.mastigias.data.lyrics.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import now.link.mastigias.domain.model.LyricsCandidate

@Serializable
data class LrcLibCandidateDto(
    @SerialName("id") val id: Long = 0L,
    @SerialName("name") val name: String? = null,
    @SerialName("trackName") val trackName: String? = null,
    @SerialName("artistName") val artistName: String? = null,
    @SerialName("albumName") val albumName: String? = null,
    @SerialName("duration") val duration: Double? = null,
    @SerialName("instrumental") val instrumental: Boolean = false,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null
) {
    fun toDomain(): LyricsCandidate? {
        val resolvedTrack = (trackName ?: name)?.trim()?.ifBlank { null } ?: return null
        val resolvedArtist = artistName?.trim() ?: ""
        return LyricsCandidate(
            id = id,
            trackName = resolvedTrack,
            artistName = resolvedArtist,
            albumName = albumName?.trim()?.ifBlank { null },
            durationSeconds = duration ?: 0.0,
            instrumental = instrumental,
            plainLyrics = plainLyrics?.ifBlank { null },
            syncedLyrics = syncedLyrics?.ifBlank { null }
        )
    }
}
