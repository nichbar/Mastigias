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
import org.junit.Assert.assertFalse
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

    @Test
    fun `filters tracks by SAF tree URI with primary volume and relative path`() = runBlocking {
        // This mirrors SAF OpenDocumentTree: uri = content://.../primary%3AMusic%2FFolderA, path = "Music/FolderA"
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FFolderA",
                    path = "Music/FolderA",
                    mode = FilterMode.INCLUDE
                )
            )
        )

        val tracks = useCase().first()

        assertEquals(1, tracks.size)
        assertEquals("Alpha", tracks[0].title)
    }

    @Test
    fun `filters tracks by SAF tree URI with primary volume parent directory`() = runBlocking {
        // Including "Music" should include tracks in both FolderA and FolderB under Music
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                    path = "Music",
                    mode = FilterMode.INCLUDE
                )
            )
        )

        val tracks = useCase().first()

        assertEquals(2, tracks.size)
        assertTrue(tracks.any { it.title == "Alpha" })
        assertTrue(tracks.any { it.title == "Beta" })
        assertTrue(tracks.none { it.title == "Gamma" }) // Gamma is in /storage/emulated/0/Downloads/
    }

    @Test
    fun `filters tracks by SAF tree URI with primary root includes all primary storage tracks`() = runBlocking {
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3A",
                    path = "",
                    mode = FilterMode.INCLUDE
                )
            )
        )

        val tracks = useCase().first()

        assertEquals(3, tracks.size)
    }

    @Test
    fun `filters tracks by relative path fallback when URI is non-SAF`() = runBlocking {
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "custom_uri",
                    path = "Music/FolderA",
                    mode = FilterMode.INCLUDE
                )
            )
        )

        val tracks = useCase().first()

        assertEquals(1, tracks.size)
        assertEquals("Alpha", tracks[0].title)
    }

    @Test
    fun `excludes tracks by SAF tree URI`() = runBlocking {
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FFolderB",
                    path = "Music/FolderB",
                    mode = FilterMode.EXCLUDE
                )
            )
        )

        val tracks = useCase().first()

        assertEquals(2, tracks.size)
        assertTrue(tracks.none { it.title == "Beta" })
    }

    @Test
    fun `FolderFilter displayName returns expected values`() {
        val folderFilter = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            path = "Music"
        )
        assertEquals("Music", folderFilter.displayName)

        val rootFilter = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/primary%3A",
            path = ""
        )
        assertEquals("Internal Storage", rootFilter.displayName)
    }

    @Test
    fun `isTrackUnderFilter matches direct files only when recursive is false`() {
        val recursiveFilter = FolderFilter(
            uri = "",
            path = "/storage/emulated/0/Music",
            recursive = true
        )
        val nonRecursiveFilter = FolderFilter(
            uri = "",
            path = "/storage/emulated/0/Music",
            recursive = false
        )

        val directFile = "/storage/emulated/0/Music/loose.mp3"
        val subfolderFile = "/storage/emulated/0/Music/Artists/Albums/track.mp3"
        val outsideFile = "/storage/emulated/0/Downloads/other.mp3"

        // Recursive: matches direct and nested
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directFile, recursiveFilter))
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(subfolderFile, recursiveFilter))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(outsideFile, recursiveFilter))

        // Non-recursive: matches direct only
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directFile, nonRecursiveFilter))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(subfolderFile, nonRecursiveFilter))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(outsideFile, nonRecursiveFilter))
    }

    @Test
    fun `isTrackUnderFilter with SAF tree URI respects recursive flag`() {
        val safRecursive = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            path = "Music",
            recursive = true
        )
        val safNonRecursive = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            path = "Music",
            recursive = false
        )

        val directTrack = "/storage/emulated/0/Music/single.mp3"
        val nestedTrack = "/storage/emulated/0/Music/Album/song.mp3"

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, safRecursive))
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, safRecursive))

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, safNonRecursive))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, safNonRecursive))
    }

    @Test
    fun `isTrackUnderFilter with SAF tree URI on secondary volume respects recursive flag`() {
        val sdRecursive = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/1234-5678%3AMusic",
            path = "Music",
            recursive = true
        )
        val sdNonRecursive = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/1234-5678%3AMusic",
            path = "Music",
            recursive = false
        )

        val directTrack = "/storage/1234-5678/Music/single.mp3"
        val nestedTrack = "/storage/1234-5678/Music/Sub/song.mp3"

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, sdRecursive))
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, sdRecursive))

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, sdNonRecursive))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, sdNonRecursive))
    }

    @Test
    fun `isTrackUnderFilter with raw SAF docId respects recursive flag`() {
        val rawRecursive = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FMusic",
            path = "Music",
            recursive = true
        )
        val rawNonRecursive = FolderFilter(
            uri = "content://com.android.externalstorage.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FMusic",
            path = "Music",
            recursive = false
        )

        val directTrack = "/storage/emulated/0/Music/single.mp3"
        val nestedTrack = "/storage/emulated/0/Music/Rock/track.mp3"

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, rawRecursive))
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, rawRecursive))

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, rawNonRecursive))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, rawNonRecursive))
    }

    @Test
    fun `isTrackUnderFilter with relative fallback respects recursive flag`() {
        val relRecursive = FolderFilter(
            uri = "custom://music",
            path = "Music",
            recursive = true
        )
        val relNonRecursive = FolderFilter(
            uri = "custom://music",
            path = "Music",
            recursive = false
        )

        val directTrack = "/storage/emulated/0/Music/single.mp3"
        val nestedTrack = "/storage/emulated/0/Music/Rock/track.mp3"

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, relRecursive))
        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, relRecursive))

        assertTrue(GetLibraryTracksUseCase.isTrackUnderFilter(directTrack, relNonRecursive))
        assertFalse(GetLibraryTracksUseCase.isTrackUnderFilter(nestedTrack, relNonRecursive))
    }

    @Test
    fun `filters tracks non-recursively includes direct files and excludes subfolder files`() = runBlocking {
        val directTrack = Track(
            id = 4L,
            path = "/storage/emulated/0/Music/root_loose.mp3",
            title = "RootLoose",
            artist = "Artist",
            album = "Loose",
            trackNumber = 1,
            durationMs = 1200L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 50L,
            dateAdded = 50L
        )
        musicRepository.tracks[directTrack.id] = directTrack

        // Non-recursive INCLUDE on /Music: should include only directTrack, not trackA (/Music/FolderA) or trackB (/Music/FolderB)
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                    path = "Music",
                    mode = FilterMode.INCLUDE,
                    recursive = false
                )
            )
        )

        val tracks = useCase().first()
        assertEquals(1, tracks.size)
        assertEquals("RootLoose", tracks[0].title)

        // Switching to recursive INCLUDE on /Music: should include directTrack, trackA, and trackB
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                    path = "Music",
                    mode = FilterMode.INCLUDE,
                    recursive = true
                )
            )
        )

        val recursiveTracks = useCase().first()
        assertEquals(3, recursiveTracks.size)
        assertTrue(recursiveTracks.any { it.title == "RootLoose" })
        assertTrue(recursiveTracks.any { it.title == "Alpha" })
        assertTrue(recursiveTracks.any { it.title == "Beta" })
        assertTrue(recursiveTracks.none { it.title == "Gamma" })
    }

    @Test
    fun `excludes tracks non-recursively leaves subfolder tracks untouched`() = runBlocking {
        val directTrack = Track(
            id = 4L,
            path = "/storage/emulated/0/Music/root_loose.mp3",
            title = "RootLoose",
            artist = "Artist",
            album = "Loose",
            trackNumber = 1,
            durationMs = 1200L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 50L,
            dateAdded = 50L
        )
        musicRepository.tracks[directTrack.id] = directTrack

        // Non-recursive EXCLUDE on /Music: excludes only root_loose.mp3, but keeps trackA (/Music/FolderA), trackB (/Music/FolderB), and trackC (/Downloads)
        preferencesRepository.setFolderFilters(
            listOf(
                FolderFilter(
                    uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                    path = "Music",
                    mode = FilterMode.EXCLUDE,
                    recursive = false
                )
            )
        )

        val tracks = useCase().first()
        assertEquals(3, tracks.size)
        assertTrue(tracks.none { it.title == "RootLoose" })
        assertTrue(tracks.any { it.title == "Alpha" })
        assertTrue(tracks.any { it.title == "Beta" })
        assertTrue(tracks.any { it.title == "Gamma" })
    }
}
