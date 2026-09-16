package now.link.mastigias.domain.usecase

import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTagEngine : TagEngine {
    var metadataResult: Result<AudioMetadata>? = null
    var artworkBytes: ByteArray? = null
    var lastWrittenPatch: TagPatch? = null

    override suspend fun readMetadata(path: String): Result<AudioMetadata> {
        return metadataResult ?: Result.failure(IllegalStateException("No metadata configured"))
    }

    override suspend fun readArtwork(path: String): Result<ByteArray?> {
        return Result.success(artworkBytes)
    }

    override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> {
        lastWrittenPatch = patch
        return Result.success(Unit)
    }
}

class ReadTrackMetadataUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var tagEngine: FakeTagEngine
    private lateinit var useCase: ReadTrackMetadataUseCase

    private val track = Track(
        id = 42L,
        path = "/storage/emulated/0/Music/song.mp3",
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        trackNumber = 1,
        durationMs = 120000L,
        hasArtwork = false,
        isTagged = true,
        dateModified = 500L
    )

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        musicRepository.tracks[track.id] = track

        tagEngine = FakeTagEngine()
        useCase = ReadTrackMetadataUseCase(musicRepository, tagEngine)
    }

    @Test
    fun `reading metadata populates trackId and metadata fields`() = runBlocking {
        tagEngine.metadataResult = Result.success(
            AudioMetadata(
                trackId = 0L,
                path = track.path,
                fields = mapOf(TagField.TITLE to "Engine Title", TagField.ARTIST to "Engine Artist"),
                artwork = null,
                bitrateKbps = 320,
                sampleRateHz = 44100,
                channels = 2,
                durationMs = 120000L
            )
        )

        val result = useCase(track.id)
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()

        assertEquals(42L, metadata.trackId)
        assertEquals("Engine Title", metadata.fields[TagField.TITLE])
        assertEquals("Engine Artist", metadata.fields[TagField.ARTIST])
    }

    @Test
    fun `reading metadata extracts embedded artwork with MIME sniffing`() = runBlocking {
        // JPEG magic bytes 0xFF, 0xD8, 0xFF
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00)
        tagEngine.metadataResult = Result.success(
            AudioMetadata(
                trackId = 0L,
                path = track.path,
                fields = emptyMap(),
                artwork = null,
                bitrateKbps = 320,
                sampleRateHz = 44100,
                channels = 2,
                durationMs = 120000L
            )
        )
        tagEngine.artworkBytes = jpegBytes

        val result = useCase(track.id)
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()

        assertNotNull(metadata.artwork)
        assertEquals("image/jpeg", metadata.artwork?.mimeType)
        assertTrue(jpegBytes.contentEquals(metadata.artwork!!.binaryData))
    }

    @Test
    fun `reading metadata extracts PNG embedded artwork with MIME sniffing`() = runBlocking {
        // PNG magic bytes: 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        val pngBytes = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(), 0x00
        )
        tagEngine.metadataResult = Result.success(
            AudioMetadata(
                trackId = 0L,
                path = track.path,
                fields = emptyMap(),
                artwork = null,
                bitrateKbps = 320,
                sampleRateHz = 44100,
                channels = 2,
                durationMs = 120000L
            )
        )
        tagEngine.artworkBytes = pngBytes

        val result = useCase(track.id)
        assertTrue(result.isSuccess)
        val metadata = result.getOrThrow()

        assertNotNull(metadata.artwork)
        assertEquals("image/png", metadata.artwork?.mimeType)
    }

    @Test
    fun `reading non-existent track returns failure`() = runBlocking {
        val result = useCase(999L)
        assertTrue(result.isFailure)
    }
}
