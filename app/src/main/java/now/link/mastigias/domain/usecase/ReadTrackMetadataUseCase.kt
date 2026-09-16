package now.link.mastigias.domain.usecase

import now.link.mastigias.core.common.ImageUtils
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.repository.MusicRepository
import javax.inject.Inject

class ReadTrackMetadataUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val tagEngine: TagEngine
) {
    suspend operator fun invoke(trackId: Long): Result<AudioMetadata> {
        val track = musicRepository.getTrackById(trackId)
            ?: return Result.failure(NoSuchElementException("Track with ID $trackId not found"))

        return invoke(track.path, track.id)
    }

    suspend operator fun invoke(path: String, trackId: Long = 0L): Result<AudioMetadata> {
        val metadataResult = tagEngine.readMetadata(path)
        if (metadataResult.isFailure) {
            return metadataResult
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

        return Result.success(
            metadata.copy(
                trackId = trackId,
                artwork = artwork
            )
        )
    }

    companion object {
        fun sniffImageMimeType(bytes: ByteArray): String = ImageUtils.sniffMimeType(bytes)
    }
}
