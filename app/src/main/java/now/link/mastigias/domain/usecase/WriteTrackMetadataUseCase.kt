package now.link.mastigias.domain.usecase

import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.repository.MusicRepository
import javax.inject.Inject

class WriteTrackMetadataUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(trackId: Long, patch: TagPatch): Result<Unit> {
        return musicRepository.writeTrackMetadata(trackId, patch)
    }

    suspend operator fun invoke(
        trackId: Long,
        updatedFields: Map<TagField, String> = emptyMap(),
        deletedFields: Set<TagField> = emptySet(),
        updatedArtwork: ArtworkData? = null,
        removeArtwork: Boolean = false
    ): Result<Unit> {
        val patch = TagPatch(
            updatedFields = updatedFields,
            deletedFields = deletedFields,
            updatedArtwork = updatedArtwork,
            removeArtwork = removeArtwork
        )
        return musicRepository.writeTrackMetadata(trackId, patch)
    }
}
