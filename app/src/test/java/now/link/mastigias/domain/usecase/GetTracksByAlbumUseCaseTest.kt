package now.link.mastigias.domain.usecase

import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GetTracksByAlbumUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var useCase: GetTracksByAlbumUseCase

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
        path = "/storage/emulated/0/Music/AlbumA/song2.mp3",
        title = "Song Two",
        artist = "Artist Alpha",
        album = "Album A",
        trackNumber = 2,
        durationMs = 200000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        musicRepository.tracks[track1.id] = track1
        musicRepository.tracks[track2.id] = track2
        useCase = GetTracksByAlbumUseCase(musicRepository)
    }

    @Test
    fun `returns tracks for valid album`() = runBlocking {
        val result = useCase("Album A")
        assertEquals(2, result.size)
        assertEquals(track1.id, result[0].id)
        assertEquals(track2.id, result[1].id)
    }

    @Test
    fun `returns empty list when album name is blank`() = runBlocking {
        val result = useCase("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns filtered tracks when artist is provided`() = runBlocking {
        val result = useCase("Album A", "Artist Alpha")
        assertEquals(2, result.size)

        val nonMatching = useCase("Album A", "Other Artist")
        assertTrue(nonMatching.isEmpty())
    }
}
