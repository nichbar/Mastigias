package now.link.mastigias.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import now.link.mastigias.domain.model.Track

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["date_modified"]),
        Index(value = ["date_added"])
    ]
)
data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: Long, // Matches MediaStore._ID
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "album") val album: String,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "has_artwork") val hasArtwork: Boolean?,
    @ColumnInfo(name = "is_tagged") val isTagged: Boolean,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "date_added", defaultValue = "0") val dateAdded: Long = 0L
)

fun TrackEntity.toDomain(): Track = Track(
    id = id,
    path = path,
    title = title,
    artist = artist,
    album = album,
    trackNumber = trackNumber,
    durationMs = durationMs,
    hasArtwork = hasArtwork,
    isTagged = isTagged,
    dateModified = dateModified,
    dateAdded = dateAdded
)

fun Track.toEntity(mimeType: String = "", sizeBytes: Long = 0L): TrackEntity = TrackEntity(
    id = id,
    path = path,
    title = title,
    artist = artist,
    album = album,
    trackNumber = trackNumber,
    durationMs = durationMs,
    hasArtwork = hasArtwork,
    isTagged = isTagged,
    dateModified = dateModified,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    dateAdded = dateAdded
)
