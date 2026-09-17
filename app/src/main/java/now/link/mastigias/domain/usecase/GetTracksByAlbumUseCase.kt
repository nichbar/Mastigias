package now.link.mastigias.domain.usecase

import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.MusicRepository
import javax.inject.Inject

class GetTracksByAlbumUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(album: String, artist: String? = null): List<Track> {
        val trimmed = album.trim()
        if (trimmed.isBlank()) {
            return emptyList()
        }
        return musicRepository.getTracksByAlbum(trimmed, artist)
    }
}
