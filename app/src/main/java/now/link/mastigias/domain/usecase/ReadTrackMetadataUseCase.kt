package now.link.mastigias.domain.usecase

import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.core.common.ImageUtils
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.repository.MusicRepository
import java.io.File
import javax.inject.Inject

class ReadTrackMetadataUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val tagEngine: TagEngine,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend operator fun invoke(trackId: Long): Result<AudioMetadata> = withContext(dispatchers.io) {
        val track = musicRepository.getTrackById(trackId)
            ?: return@withContext Result.failure(NoSuchElementException("Track with ID $trackId not found"))

        invoke(track.path, track.id)
    }

    suspend operator fun invoke(path: String, trackId: Long = 0L): Result<AudioMetadata> = withContext(dispatchers.io) {
        val metadataResult = tagEngine.readMetadata(path)
        if (metadataResult.isFailure) {
            return@withContext metadataResult
        }

        val metadata = metadataResult.getOrThrow()

        val artwork = metadata.artwork ?: run {
            val artworkBytes = tagEngine.readArtwork(path).getOrNull()
            if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                val mime = sniffImageMimeType(artworkBytes)
                val (width, height) = ImageUtils.decodeDimensions(artworkBytes)
                ArtworkData(
                    binaryData = artworkBytes,
                    mimeType = mime,
                    width = width,
                    height = height
                )
            } else {
                null
            }
        }

        val fileSize = if (metadata.fileSizeBytes > 0L) {
            metadata.fileSizeBytes
        } else {
            runCatching { File(path).length() }.getOrDefault(0L)
        }

        Result.success(
            metadata.copy(
                trackId = trackId,
                artwork = artwork,
                fileSizeBytes = fileSize
            )
        )
    }

    companion object {
        fun sniffImageMimeType(bytes: ByteArray): String = ImageUtils.sniffMimeType(bytes)
    }
}
