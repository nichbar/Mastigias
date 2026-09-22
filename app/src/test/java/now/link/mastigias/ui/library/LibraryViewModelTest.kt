package now.link.mastigias.ui.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.MusicRepository
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.usecase.GetAlbumsUseCase
import now.link.mastigias.domain.usecase.GetLibraryTracksUseCase
import now.link.mastigias.domain.usecase.SyncMediaStoreUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class TestPreferencesRepository : PreferencesRepository {
    override val sortOrderFlow = MutableStateFlow(LibrarySortOrder.TITLE)
    override val sortDirectionFlow = MutableStateFlow(SortDirection.ASCENDING)
    override val folderFiltersFlow = MutableStateFlow<List<FolderFilter>>(emptyList())
    override val viewModeFlow = MutableStateFlow(LibraryViewMode.TRACKS)

    override suspend fun setSortOrder(order: LibrarySortOrder) {
        sortOrderFlow.value = order
    }

    override suspend fun setSortDirection(direction: SortDirection) {
        sortDirectionFlow.value = direction
    }

    override suspend fun setViewMode(mode: LibraryViewMode) {
        viewModeFlow.value = mode
    }

    override suspend fun setFolderFilters(filters: List<FolderFilter>) {
        folderFiltersFlow.value = filters
    }
}

private class TestMusicRepository : MusicRepository {
    val tracksFlow = MutableStateFlow<List<Track>>(emptyList())
    val albumsFlow = MutableStateFlow<List<Album>>(emptyList())

    override fun observeTracks(): Flow<List<Track>> = tracksFlow
    override fun observeAlbums(): Flow<List<Album>> = albumsFlow
    override fun searchTracks(query: String): Flow<List<Track>> = tracksFlow
    override fun observeUntaggedTracks(): Flow<List<Track>> = tracksFlow
    override suspend fun getTrackById(id: Long): Track? = tracksFlow.value.find { it.id == id }
    override suspend fun getTracksByIds(ids: List<Long>): List<Track> = tracksFlow.value.filter { it.id in ids }
    override suspend fun getTracksByAlbum(album: String, artist: String?): List<Track> = emptyList()
    override suspend fun syncMediaStore(): Result<Unit> = Result.success(Unit)
    override suspend fun writeTrackMetadata(trackId: Long, patch: TagPatch): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTrack(trackId: Long): Result<Unit> = Result.success(Unit)
}

class LibraryViewModelTest {

    private lateinit var musicRepository: TestMusicRepository
    private lateinit var preferencesRepository: TestPreferencesRepository
    private lateinit var getLibraryTracksUseCase: GetLibraryTracksUseCase
    private lateinit var getAlbumsUseCase: GetAlbumsUseCase
    private lateinit var syncMediaStoreUseCase: SyncMediaStoreUseCase

    private val testDispatchers = AppDispatchers(
        default = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
        unconfined = Dispatchers.Unconfined
    )

    private val track1 = Track(
        id = 1L,
        path = "/storage/emulated/0/Music/song1.mp3",
        title = "Song 1",
        artist = "Artist A",
        album = "Album 1",
        trackNumber = 1,
        durationMs = 180000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 1000L
    )

    private val track2 = Track(
        id = 2L,
        path = "/storage/emulated/0/Music/song2.mp3",
        title = "Song 2",
        artist = "Artist A",
        album = "Album 1",
        trackNumber = 2,
        durationMs = 200000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 1000L
    )

    private val track3 = Track(
        id = 3L,
        path = "/storage/emulated/0/Music/song3.mp3",
        title = "Song 3",
        artist = "Artist B",
        album = "Album 2",
        trackNumber = 1,
        durationMs = 220000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 1000L
    )

    private val track4 = Track(
        id = 4L,
        path = "/storage/emulated/0/Music/song4.mp3",
        title = "Song 4",
        artist = "Artist C",
        album = "Album 1", // Same album name as track1/track2, but different artist
        trackNumber = 1,
        durationMs = 240000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 1000L
    )

    @Before
    fun setUp() {
        musicRepository = TestMusicRepository()
        musicRepository.tracksFlow.value = listOf(track1, track2, track3, track4)

        val album1 = Album(
            title = "Album 1",
            artist = "Artist A",
            tracks = listOf(track1, track2),
            coverTrackId = 1L
        )
        val album2 = Album(
            title = "Album 2",
            artist = "Artist B",
            tracks = listOf(track3),
            coverTrackId = 3L
        )
        val album3 = Album(
            title = "Album 1",
            artist = "Artist C",
            tracks = listOf(track4),
            coverTrackId = 4L
        )
        musicRepository.albumsFlow.value = listOf(album1, album2, album3)

        preferencesRepository = TestPreferencesRepository()
        getLibraryTracksUseCase = GetLibraryTracksUseCase(musicRepository, preferencesRepository)
        getAlbumsUseCase = GetAlbumsUseCase(musicRepository, preferencesRepository)
        syncMediaStoreUseCase = SyncMediaStoreUseCase(musicRepository)
    }

    private fun createViewModel(): LibraryViewModel {
        return LibraryViewModel(
            getLibraryTracksUseCase = getLibraryTracksUseCase,
            getAlbumsUseCase = getAlbumsUseCase,
            syncMediaStoreUseCase = syncMediaStoreUseCase,
            preferencesRepository = preferencesRepository,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun `toggleTrackSelection toggles selection correctly`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedTrackIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(0, viewModel.uiState.value.selectedCount)

        viewModel.toggleTrackSelection(1L)
        assertEquals(setOf(1L), viewModel.uiState.value.selectedTrackIds)
        assertTrue(viewModel.uiState.value.isSelectionMode)
        assertEquals(1, viewModel.uiState.value.selectedCount)

        viewModel.toggleTrackSelection(2L)
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedTrackIds)
        assertEquals(2, viewModel.uiState.value.selectedCount)

        viewModel.toggleTrackSelection(1L)
        assertEquals(setOf(2L), viewModel.uiState.value.selectedTrackIds)
        assertEquals(1, viewModel.uiState.value.selectedCount)

        viewModel.toggleTrackSelection(2L)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedTrackIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)
        assertEquals(0, viewModel.uiState.value.selectedCount)

        job.cancel()
    }

    @Test
    fun `clearSelection empties selected track ids`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        viewModel.toggleTrackSelection(1L)
        viewModel.toggleTrackSelection(2L)
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.clearSelection()
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedTrackIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)

        job.cancel()
    }

    @Test
    fun `toggleSelectAll selects all visible tracks and deselects all when already selected in flat tracks mode`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        // Currently in TRACKS view mode
        viewModel.toggleSelectAll()
        val allTrackIds = setOf(1L, 2L, 3L, 4L)
        assertEquals(allTrackIds, viewModel.uiState.value.selectedTrackIds)
        assertEquals(4, viewModel.uiState.value.selectedCount)

        // Calling toggleSelectAll again clears the selection
        viewModel.toggleSelectAll()
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedTrackIds)
        assertFalse(viewModel.uiState.value.isSelectionMode)

        job.cancel()
    }

    @Test
    fun `toggleAlbumSelection toggles all tracks belonging to album`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        val album1 = Album(
            title = "Album 1",
            artist = "Artist A",
            tracks = listOf(track1, track2),
            coverTrackId = 1L
        )

        viewModel.toggleAlbumSelection(album1)
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.selectedTrackIds)

        // Toggling again removes album tracks
        viewModel.toggleAlbumSelection(album1)
        assertEquals(emptySet<Long>(), viewModel.uiState.value.selectedTrackIds)

        job.cancel()
    }

    @Test
    fun `isMultiAlbumSelected returns false for zero or one selected track`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        assertFalse(viewModel.isMultiAlbumSelected())

        viewModel.toggleTrackSelection(1L)
        assertFalse(viewModel.isMultiAlbumSelected())

        job.cancel()
    }

    @Test
    fun `isMultiAlbumSelected returns false when tracks belong to same album and artist`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        viewModel.toggleTrackSelection(1L)
        viewModel.toggleTrackSelection(2L)

        assertFalse(viewModel.isMultiAlbumSelected())

        job.cancel()
    }

    @Test
    fun `isMultiAlbumSelected returns true when tracks belong to different albums`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        viewModel.toggleTrackSelection(1L) // Album 1, Artist A
        viewModel.toggleTrackSelection(3L) // Album 2, Artist B

        assertTrue(viewModel.isMultiAlbumSelected())

        job.cancel()
    }

    @Test
    fun `isMultiAlbumSelected returns true when tracks have same album title but different artists`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        viewModel.toggleTrackSelection(1L) // Album 1, Artist A
        viewModel.toggleTrackSelection(4L) // Album 1, Artist C

        assertTrue(viewModel.isMultiAlbumSelected())

        job.cancel()
    }
}
