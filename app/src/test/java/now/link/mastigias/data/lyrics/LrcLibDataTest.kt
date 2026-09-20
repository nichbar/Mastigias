package now.link.mastigias.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.lyrics.model.LrcLibCandidateDto
import now.link.mastigias.data.lyrics.remote.LrcLibClient
import now.link.mastigias.data.lyrics.repository.LrcLibRepositoryImpl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LrcLibDataTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `deserializes LrcLibCandidateDto from JSON correctly`() {
        val jsonString = """
            [
                {
                    "id": 12345,
                    "name": "Yellow Submarine",
                    "trackName": "Yellow Submarine",
                    "artistName": "The Beatles",
                    "albumName": "Revolver",
                    "duration": 158.0,
                    "instrumental": false,
                    "plainLyrics": "In the town where I was born...",
                    "syncedLyrics": "[00:07.00] In the town where I was born..."
                }
            ]
        """.trimIndent()

        val list = json.decodeFromString<List<LrcLibCandidateDto>>(jsonString)
        assertEquals(1, list.size)
        val dto = list[0]
        assertEquals(12345L, dto.id)
        assertEquals("Yellow Submarine", dto.trackName)
        assertEquals("The Beatles", dto.artistName)
        assertEquals("Revolver", dto.albumName)
        assertEquals(158.0, dto.duration ?: 0.0, 0.01)
        assertFalse(dto.instrumental)
        assertNotNull(dto.plainLyrics)
        assertNotNull(dto.syncedLyrics)

        val domain = dto.toDomain()
        assertNotNull(domain)
        assertTrue(domain!!.hasSyncedLyrics)
        assertTrue(domain.hasPlainLyrics)
        assertEquals("[00:07.00] In the town where I was born...", domain.bestLyrics)
    }

    @Test
    fun `toDomain falls back to name when trackName is null`() {
        val dto = LrcLibCandidateDto(
            id = 42L,
            name = "Fallback Title",
            trackName = null,
            artistName = "Artist"
        )
        val domain = dto.toDomain()
        assertNotNull(domain)
        assertEquals("Fallback Title", domain!!.trackName)
    }

    @Test
    fun `toDomain returns null when both trackName and name are blank`() {
        val dto = LrcLibCandidateDto(
            id = 42L,
            name = "   ",
            trackName = null,
            artistName = "Artist"
        )
        val domain = dto.toDomain()
        assertNull(domain)
    }

    @Test
    fun `LrcLibRepositoryImpl returns success with mapped candidates`() = runBlocking {
        val fakeClient = object : LrcLibClient(OkHttpClient(), json) {
            override suspend fun search(trackName: String, artistName: String?, albumName: String?): List<LrcLibCandidateDto> {
                return listOf(
                    LrcLibCandidateDto(
                        id = 100L,
                        trackName = trackName,
                        artistName = artistName ?: "Unknown",
                        duration = 180.0,
                        syncedLyrics = "[00:01.00] Test"
                    )
                )
            }
        }

        val repo = LrcLibRepositoryImpl(
            client = fakeClient,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
                main = Dispatchers.Unconfined,
                unconfined = Dispatchers.Unconfined
            )
        )

        val result = repo.searchLyrics("Test Track", "Test Artist", null)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals(100L, list[0].id)
        assertEquals("Test Track", list[0].trackName)
    }

    @Test
    fun `LrcLibRepositoryImpl handles client exception and returns failure`() = runBlocking {
        val fakeClient = object : LrcLibClient(OkHttpClient(), json) {
            override suspend fun search(trackName: String, artistName: String?, albumName: String?): List<LrcLibCandidateDto> {
                throw IOException("Connection timed out")
            }
        }

        val repo = LrcLibRepositoryImpl(
            client = fakeClient,
            dispatchers = AppDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
                main = Dispatchers.Unconfined,
                unconfined = Dispatchers.Unconfined
            )
        )

        val result = repo.searchLyrics("Test Track", "Test Artist", null)
        assertTrue(result.isFailure)
        assertEquals("Connection timed out", result.exceptionOrNull()?.message)
    }
}
