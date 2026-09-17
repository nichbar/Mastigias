package now.link.mastigias.data.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.database.dao.ArtworkStatus
import now.link.mastigias.data.database.dao.TrackDao
import now.link.mastigias.data.database.entity.TrackEntity
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers

class FakeTrackDao : TrackDao {
    private val tracks = MutableStateFlow<Map<Long, TrackEntity>>(emptyMap())

    fun setTracks(list: List<TrackEntity>) {
        tracks.value = list.associateBy { it.id }
    }

    override fun observeAll(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedByDescending { t -> t.dateModified } }

    override fun observeSortedByTitleAsc(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedBy { t -> t.title.lowercase() } }

    override fun observeSortedByTitleDesc(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedByDescending { t -> t.title.lowercase() } }

    override fun observeSortedByArtistAsc(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber })) }

    override fun observeSortedByArtistDesc(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedWith(compareByDescending<TrackEntity> { it.artist.lowercase() }.thenByDescending { it.album.lowercase() }.thenBy { it.trackNumber }) }

    override fun observeSortedByAlbumAsc(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber })) }

    override fun observeSortedByAlbumDesc(): Flow<List<TrackEntity>> =
        tracks.map { it.values.sortedWith(compareByDescending<TrackEntity> { it.album.lowercase() }.thenBy { it.trackNumber }) }

    override fun searchFts(query: String): Flow<List<TrackEntity>> =
        tracks.map {
            it.values.filter { t ->
                t.title.contains(query, ignoreCase = true) ||
                    t.artist.contains(query, ignoreCase = true) ||
                    t.album.contains(query, ignoreCase = true)
            }.sortedBy { t -> t.title.lowercase() }
        }

    override fun observeUntaggedTracks(): Flow<List<TrackEntity>> =
        tracks.map { it.values.filter { t -> !t.isTagged }.sortedBy { t -> t.title.lowercase() } }

    override suspend fun getTrackById(id: Long): TrackEntity? =
        tracks.value[id]

    override suspend fun getTracksByIds(ids: List<Long>): List<TrackEntity> =
        ids.mapNotNull { tracks.value[it] }

    override suspend fun getTracksByAlbum(album: String, artist: String?): List<TrackEntity> =
        tracks.value.values.filter {
            it.album.equals(album, ignoreCase = true) &&
                (artist == null || it.artist.equals(artist, ignoreCase = true))
        }.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))

    override suspend fun upsertTracks(tracksList: List<TrackEntity>) {
        tracks.update { current ->
            current + tracksList.associateBy { it.id }
        }
    }

    override suspend fun updateArtworkStatus(id: Long, hasArtwork: Boolean) {
        tracks.update { current ->
            val track = current[id]
            if (track != null) {
                current + (id to track.copy(hasArtwork = hasArtwork))
            } else {
                current
            }
        }
    }

    override suspend fun getArtworkStatuses(): List<ArtworkStatus> =
        tracks.value.values.map { ArtworkStatus(it.id, it.hasArtwork) }

    override suspend fun pruneDeletedTracks(validMediaStoreIds: List<Long>) {
        tracks.update { current ->
            current.filterKeys { it in validMediaStoreIds }
        }
    }

    override suspend fun deleteTrackById(id: Long) {
        tracks.update { current ->
            current - id
        }
    }

    override suspend fun deleteAllTracks() {
        tracks.value = emptyMap()
    }
}

class FakeTagEngine : TagEngine {
    override suspend fun readMetadata(path: String): Result<AudioMetadata> =
        Result.failure(UnsupportedOperationException("Fake TagEngine readMetadata"))

    override suspend fun readArtwork(path: String): Result<ByteArray?> =
        Result.success(null)

    override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> =
        Result.success(Unit)
}

class MusicRepositoryImplTest {

    private lateinit var fakeTrackDao: FakeTrackDao
    private lateinit var fakeTagEngine: FakeTagEngine
    private lateinit var repository: MusicRepositoryImpl

    @Before
    fun setup() {
        fakeTrackDao = FakeTrackDao()
        fakeTagEngine = FakeTagEngine()

        val testDispatchers = AppDispatchers(
            default = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            unconfined = Dispatchers.Unconfined
        )

        val mediaStoreDataSource = MediaStoreDataSource()
        val scopedStorageManager = ScopedStorageManager(
            fakeTagEngine,
            fakeTrackDao,
            testDispatchers
        )

        repository = MusicRepositoryImpl(
            trackDao = fakeTrackDao,
            mediaStoreDataSource = mediaStoreDataSource,
            scopedStorageManager = scopedStorageManager,
            tagEngine = fakeTagEngine,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun `observeTracks returns all tracks from database`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Song A", "Artist 1", "Album X", 1, 180000, true, true, 1000, "audio/flac", 10000),
            TrackEntity(2, "/p2", "Song B", "Artist 2", "Album Y", 2, 200000, false, true, 2000, "audio/mp3", 5000)
        )
        fakeTrackDao.setTracks(tracks)

        val observed = repository.observeTracks().first()
        assertEquals(2, observed.size)
        assertEquals("Song B", observed[0].title) // sorted by dateModified DESC
        assertEquals("Song A", observed[1].title)
    }

    @Test
    fun `observeAlbums correctly aggregates, groups, and sorts albums and tracks`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Track 2", "The Beatles", "Abbey Road", 2, 180000, false, true, 1000, "audio/flac", 10000),
            TrackEntity(2, "/p2", "Track 1", "The Beatles", "Abbey Road", 1, 150000, true, true, 2000, "audio/flac", 8000),
            TrackEntity(3, "/p3", "Intro", "Pink Floyd", "The Wall", 1, 120000, null, true, 3000, "audio/mp3", 4000)
        )
        fakeTrackDao.setTracks(tracks)

        val albums = repository.observeAlbums().first()
        assertEquals(2, albums.size)

        // Sorted alphabetically: "Abbey Road" before "The Wall"
        val abbeyRoad = albums[0]
        assertEquals("Abbey Road", abbeyRoad.title)
        assertEquals("The Beatles", abbeyRoad.artist)
        assertEquals(2, abbeyRoad.tracks.size)
        // Tracks sorted by trackNumber: Track 1 then Track 2
        assertEquals("Track 1", abbeyRoad.tracks[0].title)
        assertEquals(1, abbeyRoad.tracks[0].trackNumber)
        assertEquals("Track 2", abbeyRoad.tracks[1].title)
        assertEquals(2, abbeyRoad.tracks[1].trackNumber)
        // Cover track ID prefers track with artwork (Track 2 ID is 2)
        assertEquals(2L, abbeyRoad.coverTrackId)

        val theWall = albums[1]
        assertEquals("The Wall", theWall.title)
        assertEquals("Pink Floyd", theWall.artist)
        assertEquals(1, theWall.tracks.size)
        assertEquals(3L, theWall.coverTrackId)
    }

    @Test
    fun `searchTracks with blank query returns all tracks`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Paranoid Android", "Radiohead", "OK Computer", 2, 380000, true, true, 1000, "audio/flac", 30000)
        )
        fakeTrackDao.setTracks(tracks)

        val results = repository.searchTracks("   ").first()
        assertEquals(1, results.size)
        assertEquals("Paranoid Android", results[0].title)
    }

    @Test
    fun `searchTracks with query filters by title or artist`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Karma Police", "Radiohead", "OK Computer", 6, 260000, true, true, 1000, "audio/flac", 20000),
            TrackEntity(2, "/p2", "Hysteria", "Muse", "Absolution", 3, 227000, true, true, 2000, "audio/mp3", 8000)
        )
        fakeTrackDao.setTracks(tracks)

        val results = repository.searchTracks("Muse").first()
        assertEquals(1, results.size)
        assertEquals("Hysteria", results[0].title)
    }

    @Test
    fun `observeUntaggedTracks filters only untagged tracks`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Tagged Track", "Artist", "Album", 1, 180000, true, true, 1000, "audio/flac", 10000),
            TrackEntity(2, "/p2", "Untagged Track", "<unknown>", "<unknown>", 0, 0, null, false, 2000, "audio/mp3", 5000)
        )
        fakeTrackDao.setTracks(tracks)

        val untagged = repository.observeUntaggedTracks().first()
        assertEquals(1, untagged.size)
        assertEquals(2L, untagged[0].id)
        assertEquals("Untagged Track", untagged[0].title)
    }

    @Test
    fun `getTrackById returns track or null`() = runBlocking {
        val track = TrackEntity(5, "/p5", "Song", "Artist", "Album", 1, 180000, true, true, 1000, "audio/flac", 10000)
        fakeTrackDao.setTracks(listOf(track))

        val found = repository.getTrackById(5)
        assertNotNull(found)
        assertEquals("Song", found?.title)

        val notFound = repository.getTrackById(99)
        assertNull(notFound)
    }

    @Test
    fun `getTracksByIds returns matching tracks`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Song 1", "Artist", "Album", 1, 180000, true, true, 1000, "audio/flac", 10000),
            TrackEntity(2, "/p2", "Song 2", "Artist", "Album", 2, 190000, true, true, 2000, "audio/flac", 10000),
            TrackEntity(3, "/p3", "Song 3", "Artist", "Album", 3, 200000, true, true, 3000, "audio/flac", 10000)
        )
        fakeTrackDao.setTracks(tracks)

        val retrieved = repository.getTracksByIds(listOf(1, 3))
        assertEquals(2, retrieved.size)
        assertEquals("Song 1", retrieved[0].title)
        assertEquals("Song 3", retrieved[1].title)

        val empty = repository.getTracksByIds(emptyList())
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `getTracksByAlbum returns matching tracks`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Come Together", "The Beatles", "Abbey Road", 1, 259000, true, true, 1000, "audio/flac", 10000),
            TrackEntity(2, "/p2", "Something", "The Beatles", "Abbey Road", 2, 182000, true, true, 1000, "audio/flac", 10000),
            TrackEntity(3, "/p3", "Comfortably Numb", "Pink Floyd", "The Wall", 6, 382000, true, true, 2000, "audio/flac", 20000)
        )
        fakeTrackDao.setTracks(tracks)

        val albumTracks = repository.getTracksByAlbum("Abbey Road")
        assertEquals(2, albumTracks.size)
        assertEquals("Come Together", albumTracks[0].title)
        assertEquals("Something", albumTracks[1].title)

        val blankTracks = repository.getTracksByAlbum("   ")
        assertTrue(blankTracks.isEmpty())

        val unknownTracks = repository.getTracksByAlbum("<unknown album>")
        assertTrue(unknownTracks.isEmpty())
    }

    @Test
    fun `getTracksByAlbum with artist filter narrows down results`() = runBlocking {
        val tracks = listOf(
            TrackEntity(1, "/p1", "Song A", "Artist One", "Greatest Hits", 1, 180000, true, true, 1000, "audio/flac", 10000),
            TrackEntity(2, "/p2", "Song B", "Artist Two", "Greatest Hits", 1, 190000, true, true, 2000, "audio/flac", 10000)
        )
        fakeTrackDao.setTracks(tracks)

        val filtered = repository.getTracksByAlbum("Greatest Hits", "Artist One")
        assertEquals(1, filtered.size)
        assertEquals("Song A", filtered[0].title)

        val allHits = repository.getTracksByAlbum("Greatest Hits", null)
        assertEquals(2, allHits.size)
    }
}
