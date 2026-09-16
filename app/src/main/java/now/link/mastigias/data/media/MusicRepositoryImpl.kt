package now.link.mastigias.data.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.data.database.entity.TrackEntity
import now.link.mastigias.data.database.entity.toDomain
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.MusicRepository
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepositoryImpl @Inject constructor(
    private val trackDao: TrackDao,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val scopedStorageManager: ScopedStorageManager,
    private val tagEngine: TagEngine,
    private val dispatchers: AppDispatchers
) : MusicRepository {

    override fun observeTracks(): Flow<List<Track>> =
        trackDao.observeAll()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.default)

    override fun observeAlbums(): Flow<List<Album>> =
        trackDao.observeAll()
            .map { entities ->
                val domainTracks = entities.map { it.toDomain() }
                domainTracks
                    .groupBy { it.album.ifBlank { "<Unknown Album>" } }
                    .map { (albumTitle, tracks) ->
                        val primaryArtist = tracks.map { it.artist }
                            .filter { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
                            .groupingBy { it }
                            .eachCount()
                            .maxByOrNull { it.value }?.key
                            ?: tracks.firstOrNull()?.artist?.ifBlank { "<Unknown Artist>" }
                            ?: "<Unknown Artist>"

                        val sortedTracks = tracks.sortedWith(
                            compareBy<Track> { it.trackNumber }.thenBy { it.title.lowercase(Locale.ROOT) }
                        )
                        val coverTrackId = sortedTracks.firstOrNull { it.hasArtwork == true }?.id
                            ?: sortedTracks.firstOrNull()?.id

                        Album(
                            title = albumTitle,
                            artist = primaryArtist,
                            tracks = sortedTracks,
                            coverTrackId = coverTrackId
                        )
                    }
                    .sortedBy { it.title.lowercase(Locale.ROOT) }
            }
            .flowOn(dispatchers.default)

    override fun searchTracks(query: String): Flow<List<Track>> {
        val sanitized = query.trim()
        if (sanitized.isEmpty()) {
            return observeTracks()
        }

        val ftsQuery = sanitized.filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()
        return if (ftsQuery.isNotEmpty()) {
            trackDao.searchFts(ftsQuery)
                .map { entities -> entities.map { it.toDomain() } }
        } else {
            trackDao.observeAll().map { entities ->
                entities.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                }.map { it.toDomain() }
            }
        }.flowOn(dispatchers.default)
    }

    override fun observeUntaggedTracks(): Flow<List<Track>> =
        trackDao.observeUntaggedTracks()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(dispatchers.default)

    override suspend fun getTrackById(id: Long): Track? = withContext(dispatchers.io) {
        trackDao.getTrackById(id)?.toDomain()
    }

    override suspend fun getTracksByIds(ids: List<Long>): List<Track> = withContext(dispatchers.io) {
        if (ids.isEmpty()) emptyList()
        else trackDao.getTracksByIds(ids).map { it.toDomain() }
    }

    override suspend fun syncMediaStore(): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val mediaStoreItems = mediaStoreDataSource.queryAudioTracks()
            val existingArtworkStatuses = trackDao.getArtworkStatuses().associate { it.id to it.hasArtwork }

            val entities = mediaStoreItems.map { item ->
                val ext = item.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val isFastPath = ext == "mp3" || ext == "flac"

                var title = item.title
                var artist = item.artist
                var album = item.album
                var trackNumber = item.trackNumber
                var durationMs = item.durationMs

                // Enrich non-fast-path or incomplete tracks via TagEngine if readable
                if (!isFastPath || title.isBlank() || artist.isBlank() || album.isBlank()) {
                    val file = File(item.path)
                    if (file.exists() && file.canRead()) {
                        val meta = tagEngine.readMetadata(item.path).getOrNull()
                        if (meta != null) {
                            val metaTitle = meta.fields[TagField.TITLE]
                            val metaArtist = meta.fields[TagField.ARTIST]
                            val metaAlbum = meta.fields[TagField.ALBUM]
                            val metaTrackNumber = meta.fields[TagField.TRACK_NUMBER]?.toIntOrNull()

                            if (title.isBlank() && !metaTitle.isNullOrBlank()) title = metaTitle
                            if (artist.isBlank() && !metaArtist.isNullOrBlank()) artist = metaArtist
                            if (album.isBlank() && !metaAlbum.isNullOrBlank()) album = metaAlbum
                            if (trackNumber == 0 && metaTrackNumber != null) trackNumber = metaTrackNumber
                            if (durationMs <= 0L && meta.durationMs > 0L) durationMs = meta.durationMs
                        }
                    }
                }

                val isTagged = title.isNotBlank() &&
                    !title.equals("<unknown>", ignoreCase = true) &&
                    artist.isNotBlank() &&
                    !artist.equals("<unknown>", ignoreCase = true) &&
                    album.isNotBlank() &&
                    !album.equals("<unknown>", ignoreCase = true)

                TrackEntity(
                    id = item.id,
                    path = item.path,
                    title = title.ifBlank { File(item.path).nameWithoutExtension },
                    artist = artist.ifBlank { "<Unknown Artist>" },
                    album = album.ifBlank { "<Unknown Album>" },
                    trackNumber = trackNumber,
                    durationMs = durationMs,
                    hasArtwork = existingArtworkStatuses[item.id],
                    isTagged = isTagged,
                    dateModified = item.dateModified,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes
                )
            }

            if (entities.isNotEmpty()) {
                trackDao.upsertTracks(entities)
                val validIds = entities.map { it.id }
                trackDao.pruneDeletedTracks(validIds)
            }
        }
    }

    override suspend fun writeTrackMetadata(trackId: Long, patch: TagPatch): Result<Unit> =
        withContext(dispatchers.io) {
            val track = trackDao.getTrackById(trackId)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Track with ID $trackId not found in database")
                )

            scopedStorageManager.writeSingleTrack(
                trackId = trackId,
                sourcePath = track.path,
                patch = patch,
                mimeType = track.mimeType
            )
        }

    override suspend fun deleteTrack(trackId: Long): Result<Unit> = withContext(dispatchers.io) {
        runCatching {
            val track = trackDao.getTrackById(trackId)
            mediaStoreDataSource.deleteTrack(trackId)
            if (track != null) {
                val file = File(track.path)
                if (file.exists() && file.canWrite()) {
                    file.delete()
                }
            }
            trackDao.deleteTrackById(trackId)
        }
    }
}
