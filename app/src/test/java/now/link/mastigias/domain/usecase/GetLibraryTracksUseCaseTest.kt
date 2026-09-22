package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.LibraryViewMode
import now.link.mastigias.ui.library.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePreferencesRepository : PreferencesRepository {
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

class GetLibraryTracksUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var useCase: GetLibraryTracksUseCase

    private val trackA = Track(
        id = 1L,
        path = "/storage/emulated/0/Music/FolderA/alpha.mp3",
        title = "Alpha",
        artist = "Zeta",
        album = "Middle",
        trackNumber = 2,
        durationMs = 1000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 100L,
        dateAdded = 300L
    )

    private val trackB = Track(
        id = 2L,
        path = "/storage/emulated/0/Music/FolderB/beta.mp3",
        title = "Beta",
        artist = "Alpha",
        album = "Zero",
        trackNumber = 1,
        durationMs = 2000L,
        hasArtwork = true,
        isTagged = false,
        dateModified = 200L,
        dateAdded = 100L
    )

    private val trackC = Track(
        id = 3L,
        path = "/storage/emulated/0/Downloads/gamma.mp3",
        title = "Gamma",
        artist = "Beta",
        album = "Alpha",
        trackNumber = 3,
        durationMs = 3000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 300L,
        dateAdded = 200L
    )

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        musicRepository.tracks[trackA.id] = trackA
        musicRepository.tracks[trackB.id] = trackB
        musicRepository.tracks[trackC.id] = trackC

        preferencesRepository = FakePreferencesRepository()
        useCase = GetLibraryTracksUseCase(musicRepository, preferencesRepository)
    }

    @Test
    fun `returns all tracks sorted by title ascending by default`() = runBlocking {
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Alpha", tracks[0].title)
        assertEquals("Beta", tracks[1].title)
        assertEquals("Gamma", tracks[2].title)
    }

    @Test
    fun `sorts tracks descending when sortDirection is DESCENDING`() = runBlocking {
        preferencesRepository.setSortDirection(SortDirection.DESCENDING)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Gamma", tracks[0].title)
        assertEquals("Beta", tracks[1].title)
        assertEquals("Alpha", tracks[2].title)
    }

    @Test
    fun `sorts tracks by artist when sortOrder is ARTIST`() = runBlocking {
        preferencesRepository.setSortOrder(LibrarySortOrder.ARTIST)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Alpha", tracks[0].artist) // trackB
        assertEquals("Beta", tracks[1].artist)  // trackC
        assertEquals("Zeta", tracks[2].artist)  // trackA
    }

    @Test
    fun `sorts tracks by album when sortOrder is ALBUM`() = runBlocking {
        preferencesRepository.setSortOrder(LibrarySortOrder.ALBUM)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Alpha", tracks[0].album)  // trackC
        assertEquals("Middle", tracks[1].album) // trackA
        assertEquals("Zero", tracks[2].album)   // trackB
    }

    @Test
    fun `sorts tracks by dateModified when sortOrder is DATE_MODIFIED ascending`() = runBlocking {
        preferencesRepository.setSortOrder(LibrarySortOrder.DATE_MODIFIED)
        preferencesRepository.setSortDirection(SortDirection.ASCENDING)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Alpha", tracks[0].title) // 100L
        assertEquals("Beta", tracks[1].title)  // 200L
        assertEquals("Gamma", tracks[2].title) // 300L
    }

    @Test
    fun `sorts tracks by dateModified when sortOrder is DATE_MODIFIED descending`() = runBlocking {
        preferencesRepository.setSortOrder(LibrarySortOrder.DATE_MODIFIED)
        preferencesRepository.setSortDirection(SortDirection.DESCENDING)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Gamma", tracks[0].title) // 300L
        assertEquals("Beta", tracks[1].title)  // 200L
        assertEquals("Alpha", tracks[2].title) // 100L
    }

    @Test
    fun `sorts tracks by dateCreated when sortOrder is DATE_CREATED ascending`() = runBlocking {
        preferencesRepository.setSortOrder(LibrarySortOrder.DATE_CREATED)
        preferencesRepository.setSortDirection(SortDirection.ASCENDING)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Beta", tracks[0].title)  // dateAdded = 100L
        assertEquals("Gamma", tracks[1].title) // dateAdded = 200L
        assertEquals("Alpha", tracks[2].title) // dateAdded = 300L
    }

    @Test
    fun `sorts tracks by dateCreated when sortOrder is DATE_CREATED descending`() = runBlocking {
        preferencesRepository.setSortOrder(LibrarySortOrder.DATE_CREATED)
        preferencesRepository.setSortDirection(SortDirection.DESCENDING)
        val tracks = useCase().first()

        assertEquals(3, tracks.size)
        assertEquals("Alpha", tracks[0].title) // dateAdded = 300L
        assertEquals("Gamma", tracks[1].title) // dateAdded = 200L
        assertEquals("Beta", tracks[2].title)  // dateAdded = 100L
    }

    @Test
    fun `dateCreated falls back to dateModified when dateAdded is 0`() = runBlocking {
        val fallbackTrack = Track(
            id = 4L,
            path = "/storage/emulated/0/Music/delta.mp3",
            title = "Delta",
            artist = "Delta Artist",
            album = "Delta Album",
            trackNumber = 4,
            durationMs = 4000L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 50L,
            dateAdded = 0L
        )
        musicRepository.tracks[fallbackTrack.id] = fallbackTrack
        preferencesRepository.setSortOrder(LibrarySortOrder.DATE_CREATED)
        preferencesRepository.setSortDirection(SortDirection.ASCENDING)

        val tracks = useCase().first()

        assertEquals(4, tracks.size)
        assertEquals("Delta", tracks[0].title) // dateModified 50L fallback
        assertEquals("Beta", tracks[1].title)  // 100L
        assertEquals("Gamma", tracks[2].title) // 200L
        assertEquals("Alpha", tracks[3].title) // 300L
    }

    @Test
    fun `filters untagged tracks when untaggedOnly is true`() = runBlocking {
        val tracks = useCase(untaggedOnly = true).first()

        assertEquals(1, tracks.size)
        assertEquals("Beta", tracks[0].title)
        assertEquals(false, tracks[0].isTagged)
    }

    @Test
    fun `filters tracks by search query`() = runBlocking {
        val tracks = useCase(query = "gamma").first()

        assertEquals(1, tracks.size)
        assertEquals("Gamma", tracks[0].title)
    }

    @Test
    fun `filters tracks by folder INCLUDE filter`() = runBlocking {
        preferencesRepository.setFolderFilters(
            listOf(FolderFilter(uri = "uriA", path = "/storage/emulated/0/Music/FolderA", mode = FilterMode.INCLUDE))
        )

        val tracks = useCase().first()

        assertEquals(1, tracks.size)
        assertEquals("Alpha", tracks[0].title)
    }

    @Test
    fun `filters tracks by folder EXCLUDE filter`() = runBlocking {
        preferencesRepository.setFolderFilters(
            listOf(FolderFilter(uri = "uriB", path = "/storage/emulated/0/Music/FolderB", mode = FilterMode.EXCLUDE))
        )

        val tracks = useCase().first()

        assertEquals(2, tracks.size)
        assertTrue(tracks.none { it.path.contains("FolderB") })
    }
}
