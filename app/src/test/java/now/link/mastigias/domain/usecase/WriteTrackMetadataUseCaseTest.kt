package now.link.mastigias.domain.usecase

import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WriteTrackMetadataUseCaseTest {

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var useCase: WriteTrackMetadataUseCase

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        useCase = WriteTrackMetadataUseCase(musicRepository)
    }

    @Test
    fun `invoke with patch delegates directly to repository`() = runBlocking {
        val patch = TagPatch(
            updatedFields = mapOf(TagField.TITLE to "New Title"),
            deletedFields = setOf(TagField.COMMENT),
            updatedArtwork = null,
            removeArtwork = true
        )

        val result = useCase(10L, patch)
        assertTrue(result.isSuccess)
        assertEquals(patch, musicRepository.writtenPatches[10L])
    }

    @Test
    fun `invoke with individual parameters creates and delegates patch`() = runBlocking {
        val artwork = ArtworkData(
            binaryData = byteArrayOf(0x01, 0x02),
            mimeType = "image/jpeg"
        )

        val result = useCase(
            trackId = 20L,
            updatedFields = mapOf(TagField.ARTIST to "Solo Artist"),
            deletedFields = setOf(TagField.GENRE),
            updatedArtwork = artwork,
            removeArtwork = false
        )

        assertTrue(result.isSuccess)
        val written = musicRepository.writtenPatches[20L]
        assertEquals("Solo Artist", written?.updatedFields?.get(TagField.ARTIST))
        assertTrue(written?.deletedFields?.contains(TagField.GENRE) == true)
        assertEquals(artwork, written?.updatedArtwork)
        assertEquals(false, written?.removeArtwork)
    }
}
