package now.link.mastigias.data.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import now.link.mastigias.data.database.entity.TrackEntity

data class ArtworkStatus(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "has_artwork") val hasArtwork: Boolean?
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY date_modified DESC")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeSortedByTitleAsc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE DESC")
    fun observeSortedByTitleDesc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE ASC, album COLLATE NOCASE ASC, track_number ASC")
    fun observeSortedByArtistAsc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY artist COLLATE NOCASE DESC, album COLLATE NOCASE DESC, track_number ASC")
    fun observeSortedByArtistDesc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY album COLLATE NOCASE ASC, track_number ASC")
    fun observeSortedByAlbumAsc(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY album COLLATE NOCASE DESC, track_number ASC")
    fun observeSortedByAlbumDesc(): Flow<List<TrackEntity>>

    @Query("""
        SELECT tracks.* FROM tracks
        JOIN tracks_fts ON tracks.id = tracks_fts.rowid
        WHERE tracks_fts MATCH :query || '*'
        ORDER BY tracks.title COLLATE NOCASE ASC
    """)
    fun searchFts(query: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE is_tagged = 0 ORDER BY title COLLATE NOCASE ASC")
    fun observeUntaggedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<TrackEntity>

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET has_artwork = :hasArtwork WHERE id = :id")
    suspend fun updateArtworkStatus(id: Long, hasArtwork: Boolean)

    @Query("SELECT id, has_artwork FROM tracks")
    suspend fun getArtworkStatuses(): List<ArtworkStatus>

    @Query("DELETE FROM tracks WHERE id NOT IN (:validMediaStoreIds)")
    suspend fun pruneDeletedTracks(validMediaStoreIds: List<Long>)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Long)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
}
