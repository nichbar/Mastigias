package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.TagCategory
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.repository.MusicRepository
import now.link.mastigias.ui.editor.FieldEditState
import javax.inject.Inject

data class BatchProgress(
    val current: Int,
    val total: Int,
    val currentTitle: String,
    val failedIds: List<Long> = emptyList(),
    val isComplete: Boolean = false
)

class BatchWriteMetadataUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    operator fun invoke(
        trackIds: List<Long>,
        fieldEdits: Map<TagField, FieldEditState>,
        artworkData: ArtworkData?,
        isArtworkBatchEnabled: Boolean,
        removeArtwork: Boolean
    ): Flow<BatchProgress> = flow {
        val total = trackIds.size
        val failedIds = mutableListOf<Long>()

        if (total == 0) {
            emit(BatchProgress(current = 0, total = 0, currentTitle = "Complete", isComplete = true))
            return@flow
        }

        // Filter only enabled fields and explicitly skip Lyrics per batch safety specs
        val updatedFields = fieldEdits
            .filter { (field, state) -> state.isEnabledInBatch && field.category != TagCategory.LYRICS }
            .mapValues { it.value.value }

        // Determine fields to delete (enabled in batch with blank value)
        val toDelete = updatedFields.filter { it.value.isBlank() }.keys
        val toUpdate = updatedFields.filter { it.value.isNotBlank() }

        val batchPatch = TagPatch(
            updatedFields = toUpdate,
            deletedFields = toDelete,
            updatedArtwork = if (isArtworkBatchEnabled && !removeArtwork) artworkData else null,
            removeArtwork = if (isArtworkBatchEnabled) removeArtwork else false
        )

        val tracks = musicRepository.getTracksByIds(trackIds).associateBy { it.id }

        for ((index, trackId) in trackIds.withIndex()) {
            val track = tracks[trackId]
            val title = track?.title ?: "Track #$trackId"
            emit(BatchProgress(current = index, total = total, currentTitle = title, failedIds = failedIds.toList(), isComplete = false))

            if (track == null) {
                failedIds.add(trackId)
                continue
            }

            val result = musicRepository.writeTrackMetadata(track.id, batchPatch)
            if (result.isFailure) {
                failedIds.add(trackId)
            }
        }

        emit(BatchProgress(current = total, total = total, currentTitle = "Complete", failedIds = failedIds.toList(), isComplete = true))
    }
}
