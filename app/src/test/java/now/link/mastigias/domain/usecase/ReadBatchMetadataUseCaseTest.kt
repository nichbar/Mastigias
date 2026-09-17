package now.link.mastigias.domain.usecase

import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TestTagEngine(
    private val metadataProvider: (String) -> Result<AudioMetadata> = { Result.failure(IllegalStateException("No metadata")) }
) : TagEngine {
    override suspend fun readMetadata(path: String): Result<AudioMetadata> = metadataProvider(path)
    override suspend fun readArtwork(path: String): Result<ByteArray?> = Result.success(null)
    override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> = Result.success(Unit)
}

class ReadBatchMetadataUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var readTrackMetadataUseCase: ReadTrackMetadataUseCase
    private lateinit var readBatchMetadataUseCase: ReadBatchMetadataUseCase

    private val track1 = Track(
        id = 1L,
        path = "/music/song1.mp3",
        title = "Track 1",
        artist = "Shared Artist",
        album = "Shared Album",
        trackNumber = 1,
        durationMs = 100000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    private val track2 = Track(
        id = 2L,
        path = "/music/song2.mp3",
        title = "Track 2",
        artist = "Shared Artist",
        album = "Shared Album",
        trackNumber = 2,
        durationMs = 200000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 2000L
    )

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        musicRepository.tracks[track1.id] = track1
        musicRepository.tracks[track2.id] = track2
    }

    @Test
    fun `empty trackIds returns failure`() = runBlocking {
        val tagEngine = TestTagEngine()
        readTrackMetadataUseCase = ReadTrackMetadataUseCase(musicRepository, tagEngine)
        readBatchMetadataUseCase = ReadBatchMetadataUseCase(readTrackMetadataUseCase)

        val result = readBatchMetadataUseCase(emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun `shared field values across all tracks are pre-filled and marked not mixed`() = runBlocking {
        val artwork = ArtworkData(byteArrayOf(1, 2, 3), "image/jpeg", 500, 500)
        val tagEngine = TestTagEngine(
            metadataProvider = {
                Result.success(
                    AudioMetadata(
                        trackId = 0L,
                        path = "",
                        fields = mapOf(
                            TagField.TITLE to "Unique Title",
                            TagField.ARTIST to "The Beatles",
                            TagField.ALBUM to "Abbey Road",
                            TagField.YEAR to "1969",
                            TagField.TRACK_NUMBER to "1"
                        ),
                        artwork = artwork,
                        bitrateKbps = 320,
                        sampleRateHz = 44100,
                        channels = 2,
                        durationMs = 180000L
                    )
                )
            }
        )
        readTrackMetadataUseCase = ReadTrackMetadataUseCase(musicRepository, tagEngine)
        readBatchMetadataUseCase = ReadBatchMetadataUseCase(readTrackMetadataUseCase)

        val result = readBatchMetadataUseCase(listOf(1L, 2L))
        assertTrue(result.isSuccess)
        val batchMetadata = result.getOrThrow()

        // Shared fields
        assertEquals("The Beatles", batchMetadata.fields[TagField.ARTIST]?.value)
        assertFalse(batchMetadata.fields[TagField.ARTIST]?.isMixed == true)
        assertEquals("Abbey Road", batchMetadata.fields[TagField.ALBUM]?.value)
        assertFalse(batchMetadata.fields[TagField.ALBUM]?.isMixed == true)
        assertEquals("1969", batchMetadata.fields[TagField.YEAR]?.value)
        assertFalse(batchMetadata.fields[TagField.YEAR]?.isMixed == true)

        // Track-specific fields must NOT be present
        assertNull(batchMetadata.fields[TagField.TITLE])
        assertNull(batchMetadata.fields[TagField.TRACK_NUMBER])

        // Artwork
        assertNotNull(batchMetadata.artwork)
        assertFalse(batchMetadata.hasMixedArtwork)
    }

    @Test
    fun `differing field values across tracks are marked mixed with empty value`() = runBlocking {
        val tagEngine = TestTagEngine(
            metadataProvider = { path ->
                if (path == track1.path) {
                    Result.success(
                        AudioMetadata(
                            trackId = 1L,
                            path = path,
                            fields = mapOf(
                                TagField.ARTIST to "Queen",
                                TagField.ALBUM to "Soundtrack",
                                TagField.GENRE to "Rock"
                            ),
                            artwork = null,
                            bitrateKbps = 320,
                            sampleRateHz = 44100,
                            channels = 2,
                            durationMs = 1000L
                        )
                    )
                } else {
                    Result.success(
                        AudioMetadata(
                            trackId = 2L,
                            path = path,
                            fields = mapOf(
                                TagField.ARTIST to "David Bowie",
                                TagField.ALBUM to "Soundtrack",
                                TagField.GENRE to "Glam Rock"
                            ),
                            artwork = null,
                            bitrateKbps = 320,
                            sampleRateHz = 44100,
                            channels = 2,
                            durationMs = 1000L
                        )
                    )
                }
            }
        )

        readTrackMetadataUseCase = ReadTrackMetadataUseCase(musicRepository, tagEngine)
        readBatchMetadataUseCase = ReadBatchMetadataUseCase(readTrackMetadataUseCase)

        val result = readBatchMetadataUseCase(listOf(1L, 2L))
        assertTrue(result.isSuccess)
        val batchMetadata = result.getOrThrow()

        // ALBUM is identical
        assertEquals("Soundtrack", batchMetadata.fields[TagField.ALBUM]?.value)
        assertFalse(batchMetadata.fields[TagField.ALBUM]?.isMixed == true)

        // ARTIST and GENRE differ
        assertEquals("", batchMetadata.fields[TagField.ARTIST]?.value)
        assertTrue(batchMetadata.fields[TagField.ARTIST]?.isMixed == true)
        assertEquals("", batchMetadata.fields[TagField.GENRE]?.value)
        assertTrue(batchMetadata.fields[TagField.GENRE]?.isMixed == true)
    }
}
