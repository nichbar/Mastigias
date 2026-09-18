package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.TagCategory
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.MusicRepository
import now.link.mastigias.ui.editor.FieldEditState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

open class FakeMusicRepository : MusicRepository {
    val tracks = mutableMapOf<Long, Track>()
    val writtenPatches = mutableMapOf<Long, TagPatch>()
    val shouldFailIds = mutableSetOf<Long>()
    var syncCalled = false

    override fun observeTracks(): Flow<List<Track>> = MutableStateFlow(tracks.values.toList())
    override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(emptyList())
    override fun searchTracks(query: String): Flow<List<Track>> = MutableStateFlow(
        tracks.values.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }
    )
    override fun observeUntaggedTracks(): Flow<List<Track>> = MutableStateFlow(
        tracks.values.filter { !it.isTagged }
    )
    override suspend fun getTrackById(id: Long): Track? = tracks[id]
    override suspend fun getTracksByIds(ids: List<Long>): List<Track> = ids.mapNotNull { tracks[it] }
    override suspend fun getTracksByAlbum(album: String, artist: String?): List<Track> =
        tracks.values.filter {
            it.album.equals(album, ignoreCase = true) &&
                (artist == null || it.artist.equals(artist, ignoreCase = true))
        }.sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))

    override suspend fun syncMediaStore(): Result<Unit> {
        syncCalled = true
        return Result.success(Unit)
    }

    override suspend fun writeTrackMetadata(trackId: Long, patch: TagPatch): Result<Unit> {
        if (shouldFailIds.contains(trackId)) {
            return Result.failure(RuntimeException("Write failed for track $trackId"))
        }
        writtenPatches[trackId] = patch
        return Result.success(Unit)
    }

    override suspend fun deleteTrack(trackId: Long): Result<Unit> {
        tracks.remove(trackId)
        return Result.success(Unit)
    }
}

class BatchWriteMetadataUseCaseTest {

    private lateinit var fakeRepository: FakeMusicRepository
    private lateinit var useCase: BatchWriteMetadataUseCase

    private val track1 = Track(
        id = 1L,
        path = "/storage/emulated/0/Music/track1.mp3",
        title = "Track One",
        artist = "Artist A",
        album = "Album A",
        trackNumber = 1,
        durationMs = 180000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    private val track2 = Track(
        id = 2L,
        path = "/storage/emulated/0/Music/track2.mp3",
        title = "Track Two",
        artist = "Artist A",
        album = "Album A",
        trackNumber = 2,
        durationMs = 200000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    @Before
    fun setUp() {
        fakeRepository = FakeMusicRepository()
        fakeRepository.tracks[1L] = track1
        fakeRepository.tracks[2L] = track2
        useCase = BatchWriteMetadataUseCase(fakeRepository)
    }

    @Test
    fun `batch write updates enabled fields and ignores disabled fields`() = runBlocking {
        val fieldEdits = mapOf(
            TagField.ARTIST to FieldEditState(isEnabledInBatch = true, value = "Updated Artist", isDirty = true),
            TagField.GENRE to FieldEditState(isEnabledInBatch = false, value = "Ignored Genre", isDirty = true)
        )

        val progressList = useCase(
            trackIds = listOf(1L, 2L),
            fieldEdits = fieldEdits,
            artworkData = null,
            isArtworkBatchEnabled = false,
            removeArtwork = false
        ).toList()

        assertEquals(3, progressList.size)
        assertEquals("Complete", progressList.last().currentTitle)
        assertTrue(progressList.last().failedIds.isEmpty())

        assertEquals("Updated Artist", fakeRepository.writtenPatches[1L]?.updatedFields?.get(TagField.ARTIST))
        assertEquals(null, fakeRepository.writtenPatches[1L]?.updatedFields?.get(TagField.GENRE))
        assertEquals("Updated Artist", fakeRepository.writtenPatches[2L]?.updatedFields?.get(TagField.ARTIST))
    }

    @Test
    fun `batch write skips lyrics field for batch safety`() = runBlocking {
        val fieldEdits = mapOf(
            TagField.ALBUM to FieldEditState(isEnabledInBatch = true, value = "Batch Album"),
            TagField.LYRICS to FieldEditState(isEnabledInBatch = true, value = "Lyrics text that must be skipped")
        )

        val progressList = useCase(
            trackIds = listOf(1L),
            fieldEdits = fieldEdits,
            artworkData = null,
            isArtworkBatchEnabled = false,
            removeArtwork = false
        ).toList()

        assertEquals(2, progressList.size)
        val patch = fakeRepository.writtenPatches[1L]
        assertEquals("Batch Album", patch?.updatedFields?.get(TagField.ALBUM))
        assertEquals(null, patch?.updatedFields?.get(TagField.LYRICS))
    }

    @Test
    fun `batch write adds blank enabled fields to deletedFields`() = runBlocking {
        val fieldEdits = mapOf(
            TagField.COMMENT to FieldEditState(isEnabledInBatch = true, value = "   "),
            TagField.ALBUM_ARTIST to FieldEditState(isEnabledInBatch = true, value = "New Album Artist")
        )

        useCase(
            trackIds = listOf(1L),
            fieldEdits = fieldEdits,
            artworkData = null,
            isArtworkBatchEnabled = false,
            removeArtwork = false
        ).toList()

        val patch = fakeRepository.writtenPatches[1L]
        assertTrue(patch?.deletedFields?.contains(TagField.COMMENT) == true)
        assertEquals("New Album Artist", patch?.updatedFields?.get(TagField.ALBUM_ARTIST))
    }

    @Test
    fun `batch write handles artwork when enabled`() = runBlocking {
        val dummyArtwork = ArtworkData(
            binaryData = byteArrayOf(0x01, 0x02),
            mimeType = "image/jpeg"
        )

        useCase(
            trackIds = listOf(1L),
            fieldEdits = emptyMap(),
            artworkData = dummyArtwork,
            isArtworkBatchEnabled = true,
            removeArtwork = false
        ).toList()

        val patch = fakeRepository.writtenPatches[1L]
        assertEquals(dummyArtwork, patch?.updatedArtwork)
        assertEquals(false, patch?.removeArtwork)
    }

    @Test
    fun `batch write handles remove artwork when enabled`() = runBlocking {
        useCase(
            trackIds = listOf(1L),
            fieldEdits = emptyMap(),
            artworkData = null,
            isArtworkBatchEnabled = true,
            removeArtwork = true
        ).toList()

        val patch = fakeRepository.writtenPatches[1L]
        assertEquals(null, patch?.updatedArtwork)
        assertEquals(true, patch?.removeArtwork)
    }

    @Test
    fun `batch write records failed IDs for failing tracks`() = runBlocking {
        fakeRepository.shouldFailIds.add(2L)

        val progressList = useCase(
            trackIds = listOf(1L, 2L, 999L), // 999L does not exist
            fieldEdits = mapOf(TagField.GENRE to FieldEditState(isEnabledInBatch = true, value = "Rock")),
            artworkData = null,
            isArtworkBatchEnabled = false,
            removeArtwork = false
        ).toList()

        val last = progressList.last()
        assertEquals(listOf(2L, 999L), last.failedIds)
        assertEquals(3, last.total)
    }

    @Test
    fun `batch write emissions have isComplete false during processing and true only at completion`() = runBlocking {
        val progressList = useCase(
            trackIds = listOf(1L, 2L),
            fieldEdits = mapOf(TagField.ARTIST to FieldEditState(isEnabledInBatch = true, value = "New Artist")),
            artworkData = null,
            isArtworkBatchEnabled = false,
            removeArtwork = false
        ).toList()

        assertEquals(3, progressList.size)
        // First track in-progress: current=0, total=2, isComplete=false
        assertEquals(0, progressList[0].current)
        assertEquals(2, progressList[0].total)
        assertEquals(false, progressList[0].isComplete)

        // Second track in-progress: current=1, total=2, isComplete=false
        assertEquals(1, progressList[1].current)
        assertEquals(2, progressList[1].total)
        assertEquals(false, progressList[1].isComplete)

        // Completion: current=2, total=2, isComplete=true
        assertEquals(2, progressList[2].current)
        assertEquals(2, progressList[2].total)
        assertEquals(true, progressList[2].isComplete)
    }

    @Test
    fun `batch write with empty trackIds emits single completion progress`() = runBlocking {
        val progressList = useCase(
            trackIds = emptyList(),
            fieldEdits = emptyMap(),
            artworkData = null,
            isArtworkBatchEnabled = false,
            removeArtwork = false
        ).toList()

        assertEquals(1, progressList.size)
        assertTrue(progressList[0].isComplete)
        assertEquals(0, progressList[0].total)
        assertEquals(0, progressList[0].current)
    }
}
