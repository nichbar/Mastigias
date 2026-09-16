package now.link.mastigias.domain.repository

import kotlinx.coroutines.flow.Flow
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track

interface MusicRepository {
    fun observeTracks(): Flow<List<Track>>
    fun observeAlbums(): Flow<List<Album>>
    fun searchTracks(query: String): Flow<List<Track>>
    fun observeUntaggedTracks(): Flow<List<Track>>
    suspend fun getTrackById(id: Long): Track?
    suspend fun getTracksByIds(ids: List<Long>): List<Track>
    suspend fun syncMediaStore(): Result<Unit>
    suspend fun writeTrackMetadata(trackId: Long, patch: TagPatch): Result<Unit>
    suspend fun deleteTrack(trackId: Long): Result<Unit>
}
