package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.Track
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetAlbumsUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var preferencesRepository: FakePreferencesRepository
    private lateinit var useCase: GetAlbumsUseCase

    private val track1 = Track(
        id = 1L,
        path = "/storage/emulated/0/Music/AlbumA/song1.mp3",
        title = "Song One",
        artist = "Artist Alpha",
        album = "Album A",
        trackNumber = 1,
        durationMs = 180000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    private val track2 = Track(
        id = 2L,
        path = "/storage/emulated/0/Music/AlbumB/song2.mp3",
        title = "Song Two",
        artist = "Artist Beta",
        album = "Album B",
        trackNumber = 1,
        durationMs = 200000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    private val albumA = Album(
        title = "Album A",
        artist = "Artist Alpha",
        tracks = listOf(track1),
        coverTrackId = 1L
    )

    private val albumB = Album(
        title = "Album B",
        artist = "Artist Beta",
        tracks = listOf(track2),
        coverTrackId = 2L
    )

    @Before
    fun setUp() {
        musicRepository = object : FakeMusicRepository() {
            override fun observeAlbums(): Flow<List<Album>> = MutableStateFlow(listOf(albumA, albumB))
        }
        preferencesRepository = FakePreferencesRepository()
        useCase = GetAlbumsUseCase(musicRepository, preferencesRepository)
    }

    @Test
    fun `returns all albums sorted ascending by default`() = runBlocking {
        val albums = useCase().first()

        assertEquals(2, albums.size)
        assertEquals("Album A", albums[0].title)
        assertEquals("Album B", albums[1].title)
    }

    @Test
    fun `sorts albums descending when sortDirection is DESCENDING`() = runBlocking {
        val albums = useCase(sortDirection = SortDirection.DESCENDING).first()

        assertEquals(2, albums.size)
        assertEquals("Album B", albums[0].title)
        assertEquals("Album A", albums[1].title)
    }

    @Test
    fun `filters albums by query on album title or artist`() = runBlocking {
        val albums = useCase(query = "Beta").first()

        assertEquals(1, albums.size)
        assertEquals("Album B", albums[0].title)
    }

    @Test
    fun `filters albums by folder filter`() = runBlocking {
        preferencesRepository.setFolderFilters(
            listOf(FolderFilter(uri = "uriA", path = "/storage/emulated/0/Music/AlbumA", mode = FilterMode.INCLUDE))
        )

        val albums = useCase().first()

        assertEquals(1, albums.size)
        assertEquals("Album A", albums[0].title)
    }

    @Test
    fun `sorts albums by artist when sortOrder is ARTIST`() = runBlocking {
        val albums = useCase(sortOrder = LibrarySortOrder.ARTIST).first()

        assertEquals(2, albums.size)
        assertEquals("Artist Alpha", albums[0].artist)
        assertEquals("Artist Beta", albums[1].artist)
    }

    @Test
    fun `filters out albums with no untagged tracks when untaggedOnly is true`() = runBlocking {
        val albums = useCase(untaggedOnly = true).first()
        assertTrue(albums.isEmpty())
    }
}
