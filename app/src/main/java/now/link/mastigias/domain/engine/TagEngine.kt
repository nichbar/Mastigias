package now.link.mastigias.domain.engine

import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagPatch

interface TagEngine {
    suspend fun readMetadata(path: String): Result<AudioMetadata>
    suspend fun readArtwork(path: String): Result<ByteArray?>
    suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit>
}
