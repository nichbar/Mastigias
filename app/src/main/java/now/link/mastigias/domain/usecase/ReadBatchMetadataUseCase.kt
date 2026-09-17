package now.link.mastigias.domain.usecase

import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.BatchFieldInfo
import now.link.mastigias.domain.model.BatchMetadata
import now.link.mastigias.domain.model.TagField
import javax.inject.Inject

class ReadBatchMetadataUseCase @Inject constructor(
    private val readTrackMetadataUseCase: ReadTrackMetadataUseCase,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend operator fun invoke(trackIds: List<Long>): Result<BatchMetadata> = withContext(dispatchers.io) {
        if (trackIds.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Track IDs must not be empty"))
        }

        val metadataList = mutableListOf<AudioMetadata>()
        for (id in trackIds) {
            val result = readTrackMetadataUseCase(id)
            if (result.isSuccess) {
                metadataList.add(result.getOrThrow())
            }
        }

        if (metadataList.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Failed to read metadata for any track"))
        }

        // Collect all batch-editable fields that exist in batchBasicFields or in any track's metadata
        val allFieldsToCheck = mutableSetOf<TagField>()
        allFieldsToCheck.addAll(TagField.batchBasicFields)
        for (metadata in metadataList) {
            for (field in metadata.fields.keys) {
                if (field.isBatchEditable) {
                    allFieldsToCheck.add(field)
                }
            }
        }

        val fieldMap = mutableMapOf<TagField, BatchFieldInfo>()
        for (field in allFieldsToCheck) {
            val values = metadataList.map { it.fields[field] ?: "" }
            val firstValue = values.first()
            val allSame = values.all { it == firstValue }

            if (allSame) {
                fieldMap[field] = BatchFieldInfo(
                    value = firstValue,
                    isMixed = false
                )
            } else {
                fieldMap[field] = BatchFieldInfo(
                    value = "",
                    isMixed = true
                )
            }
        }

        // Artwork resolution
        val artworks = metadataList.map { it.artwork }
        val nonNullArtworks = artworks.filterNotNull()
        val firstArtwork = nonNullArtworks.firstOrNull()

        val hasMixedArtwork = if (nonNullArtworks.isEmpty()) {
            false
        } else if (nonNullArtworks.size < metadataList.size) {
            true
        } else {
            val firstBytes = firstArtwork?.binaryData
            nonNullArtworks.any { !it.binaryData.contentEquals(firstBytes) }
        }

        Result.success(
            BatchMetadata(
                fields = fieldMap,
                artwork = firstArtwork,
                hasMixedArtwork = hasMixedArtwork
            )
        )
    }
}
