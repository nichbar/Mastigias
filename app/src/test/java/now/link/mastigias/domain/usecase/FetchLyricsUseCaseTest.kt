package now.link.mastigias.domain.usecase

import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.logging.NoOpAppLogger
import now.link.mastigias.domain.model.LyricsCandidate
import now.link.mastigias.domain.repository.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FetchLyricsUseCaseTest {

    private lateinit var fakeRepository: FakeLyricsRepository
    private lateinit var useCase: FetchLyricsUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeLyricsRepository()
        useCase = FetchLyricsUseCase(fakeRepository, NoOpAppLogger)
    }

    @Test
    fun `invoke with blank track name returns failure`() = runBlocking {
        val result = useCase(trackName = "   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invoke ranks exact duration match higher than mismatched duration`() = runBlocking {
        fakeRepository.candidatesToReturn = listOf(
            LyricsCandidate(
                id = 1,
                trackName = "Wonderwall",
                artistName = "Oasis",
                durationSeconds = 300.0, // 5:00, off by 42s
                syncedLyrics = "[00:01.00] Today..."
            ),
            LyricsCandidate(
                id = 2,
                trackName = "Wonderwall",
                artistName = "Oasis",
                durationSeconds = 258.0, // 4:18, exact match (258000ms)
                syncedLyrics = "[00:01.00] Today is gonna be..."
            )
        )

        val result = useCase(
            trackName = "Wonderwall",
            artistName = "Oasis",
            targetDurationMs = 258000L
        )

        assertTrue(result.isSuccess)
        val sorted = result.getOrThrow()
        assertEquals(2, sorted.size)
        assertEquals(2L, sorted[0].id)
        assertEquals(1L, sorted[1].id)
    }

    @Test
    fun `invoke ranks synced lyrics higher than plain lyrics when other factors equal`() = runBlocking {
        fakeRepository.candidatesToReturn = listOf(
            LyricsCandidate(
                id = 1,
                trackName = "Song A",
                artistName = "Artist B",
                durationSeconds = 200.0,
                plainLyrics = "Plain lyrics text only",
                syncedLyrics = null
            ),
            LyricsCandidate(
                id = 2,
                trackName = "Song A",
                artistName = "Artist B",
                durationSeconds = 200.0,
                plainLyrics = "Plain lyrics text",
                syncedLyrics = "[00:01.00] Synced line"
            )
        )

        val result = useCase(
            trackName = "Song A",
            artistName = "Artist B",
            targetDurationMs = 200000L
        )

        assertTrue(result.isSuccess)
        val sorted = result.getOrThrow()
        assertEquals(2L, sorted[0].id)
        assertEquals(1L, sorted[1].id)
    }

    @Test
    fun `invoke penalizes instrumental tracks`() = runBlocking {
        fakeRepository.candidatesToReturn = listOf(
            LyricsCandidate(
                id = 1,
                trackName = "Intro",
                artistName = "Band",
                durationSeconds = 60.0,
                instrumental = true
            ),
            LyricsCandidate(
                id = 2,
                trackName = "Intro",
                artistName = "Band",
                durationSeconds = 60.0,
                plainLyrics = "Some vocal spoken words",
                instrumental = false
            )
        )

        val result = useCase(
            trackName = "Intro",
            artistName = "Band",
            targetDurationMs = 60000L
        )

        assertTrue(result.isSuccess)
        val sorted = result.getOrThrow()
        assertEquals(2L, sorted[0].id)
    }

    @Test
    fun `invoke handles repository error gracefully`() = runBlocking {
        fakeRepository.shouldFail = true

        val result = useCase(
            trackName = "Failed Track",
            artistName = "Failed Artist"
        )

        assertTrue(result.isFailure)
        assertEquals("Network connection failed", result.exceptionOrNull()?.message)
    }
}

class FakeLyricsRepository : LyricsRepository {
    var candidatesToReturn: List<LyricsCandidate> = emptyList()
    var shouldFail: Boolean = false

    override suspend fun searchLyrics(
        trackName: String,
        artistName: String?,
        albumName: String?
    ): Result<List<LyricsCandidate>> {
        return if (shouldFail) {
            Result.failure(RuntimeException("Network connection failed"))
        } else {
            Result.success(candidatesToReturn)
        }
    }
}
