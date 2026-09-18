package now.link.mastigias.ui.editor

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.media.MediaStoreDataSource
import now.link.mastigias.domain.engine.TagEngine
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.model.TagPatch
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.usecase.BatchWriteMetadataUseCase
import now.link.mastigias.domain.usecase.FakeMusicRepository
import now.link.mastigias.domain.usecase.GetTracksByAlbumUseCase
import now.link.mastigias.domain.usecase.ReadBatchMetadataUseCase
import now.link.mastigias.domain.usecase.ReadTrackMetadataUseCase
import now.link.mastigias.domain.usecase.WriteTrackMetadataUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EditorViewModelTest {

    private val testDispatchers = AppDispatchers(
        default = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
        unconfined = Dispatchers.Unconfined
    )

    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var readTrackMetadataUseCase: ReadTrackMetadataUseCase
    private lateinit var readBatchMetadataUseCase: ReadBatchMetadataUseCase
    private lateinit var writeTrackMetadataUseCase: WriteTrackMetadataUseCase
    private lateinit var batchWriteMetadataUseCase: BatchWriteMetadataUseCase
    private lateinit var getTracksByAlbumUseCase: GetTracksByAlbumUseCase
    private lateinit var mediaStoreDataSource: MediaStoreDataSource

    private val track1 = Track(
        id = 1L,
        path = "/music/song1.mp3",
        title = "Track 1",
        artist = "Common Artist",
        album = "Common Album",
        trackNumber = 1,
        durationMs = 180000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 1000L
    )

    private val track2 = Track(
        id = 2L,
        path = "/music/song2.mp3",
        title = "Track 2",
        artist = "Different Artist",
        album = "Common Album",
        trackNumber = 2,
        durationMs = 200000L,
        hasArtwork = true,
        isTagged = true,
        dateModified = 2000L
    )

    private val artwork = ArtworkData(byteArrayOf(9, 8, 7), "image/jpeg", 300, 300)

    @Before
    fun setUp() {
        musicRepository = FakeMusicRepository()
        musicRepository.tracks[track1.id] = track1
        musicRepository.tracks[track2.id] = track2

        val tagEngine = object : TagEngine {
            override suspend fun readMetadata(path: String): Result<AudioMetadata> {
                val artist = if (path == track1.path) "Common Artist" else "Different Artist"
                return Result.success(
                    AudioMetadata(
                        trackId = if (path == track1.path) 1L else 2L,
                        path = path,
                        fields = mapOf(
                            TagField.TITLE to if (path == track1.path) "Track 1" else "Track 2",
                            TagField.ARTIST to artist,
                            TagField.ALBUM to "Common Album",
                            TagField.YEAR to "2020",
                            TagField.GENRE to "Rock"
                        ),
                        artwork = artwork,
                        bitrateKbps = 320,
                        sampleRateHz = 44100,
                        channels = 2,
                        durationMs = 180000L
                    )
                )
            }

            override suspend fun readArtwork(path: String): Result<ByteArray?> = Result.success(artwork.binaryData)

            override suspend fun writeMetadata(path: String, patch: TagPatch): Result<Unit> = Result.success(Unit)
        }

        readTrackMetadataUseCase = ReadTrackMetadataUseCase(musicRepository, tagEngine, testDispatchers)
        readBatchMetadataUseCase = ReadBatchMetadataUseCase(readTrackMetadataUseCase, testDispatchers)
        writeTrackMetadataUseCase = WriteTrackMetadataUseCase(musicRepository)
        batchWriteMetadataUseCase = BatchWriteMetadataUseCase(musicRepository)
        getTracksByAlbumUseCase = GetTracksByAlbumUseCase(musicRepository)
        mediaStoreDataSource = MediaStoreDataSource()
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): EditorViewModel {
        return EditorViewModel(
            savedStateHandle = savedStateHandle,
            readTrackMetadataUseCase = readTrackMetadataUseCase,
            readBatchMetadataUseCase = readBatchMetadataUseCase,
            writeTrackMetadataUseCase = writeTrackMetadataUseCase,
            batchWriteMetadataUseCase = batchWriteMetadataUseCase,
            getTracksByAlbumUseCase = getTracksByAlbumUseCase,
            mediaStoreDataSource = mediaStoreDataSource,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun `batch mode loads shared fields prefilled and mixed fields as empty with isMixed true`() = runBlocking {
        val viewModel = createViewModel()
        viewModel.initialize(longArrayOf(1L, 2L))

        val state = viewModel.uiState.value
        assertTrue(state.mode is EditorMode.Batch)
        assertFalse(state.isDirty)

        // ALBUM is shared across tracks -> prefilled and not mixed
        val albumField = state.fields[TagField.ALBUM]
        assertNotNull(albumField)
        assertEquals("Common Album", albumField?.value)
        assertFalse(albumField?.isMixed == true)
        assertFalse(albumField?.isDirty == true)

        // YEAR is shared across tracks -> prefilled and not mixed
        val yearField = state.fields[TagField.YEAR]
        assertNotNull(yearField)
        assertEquals("2020", yearField?.value)
        assertFalse(yearField?.isMixed == true)

        // ARTIST differs between tracks -> empty value and isMixed = true
        val artistField = state.fields[TagField.ARTIST]
        assertNotNull(artistField)
        assertEquals("", artistField?.value)
        assertTrue(artistField?.isMixed == true)
        assertFalse(artistField?.isDirty == true)

        // Track-specific fields must not be present
        assertNull(state.fields[TagField.TITLE])
        assertNull(state.fields[TagField.TRACK_NUMBER])

        // Artwork is pre-loaded from tracks
        assertNotNull(state.artwork)
        assertFalse(state.isArtworkDirty)
    }

    @Test
    fun `modifying shared field marks it dirty and enabled in batch`() = runBlocking {
        val viewModel = createViewModel()
        viewModel.initialize(longArrayOf(1L, 2L))

        // Edit ALBUM
        viewModel.updateField(TagField.ALBUM, "Updated Album")

        val albumField = viewModel.uiState.value.fields[TagField.ALBUM]
        assertEquals("Updated Album", albumField?.value)
        assertTrue(albumField?.isDirty == true)
        assertTrue(albumField?.isEnabledInBatch == true)
        assertTrue(viewModel.uiState.value.isDirty)

        // Revert ALBUM back to initial value
        viewModel.updateField(TagField.ALBUM, "Common Album")
        val revertedField = viewModel.uiState.value.fields[TagField.ALBUM]
        assertEquals("Common Album", revertedField?.value)
        assertFalse(revertedField?.isDirty == true)
        assertFalse(revertedField?.isEnabledInBatch == true)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `typing in mixed field marks it dirty and backspacing to empty restores mixed state`() = runBlocking {
        val viewModel = createViewModel()
        viewModel.initialize(longArrayOf(1L, 2L))

        val initialArtist = viewModel.uiState.value.fields[TagField.ARTIST]
        assertTrue(initialArtist?.isMixed == true)

        // User enters a value for ARTIST
        viewModel.updateField(TagField.ARTIST, "Unified Artist")
        val editedArtist = viewModel.uiState.value.fields[TagField.ARTIST]
        assertEquals("Unified Artist", editedArtist?.value)
        assertFalse(editedArtist?.isMixed == true)
        assertTrue(editedArtist?.isDirty == true)
        assertTrue(editedArtist?.isEnabledInBatch == true)
        assertTrue(viewModel.uiState.value.isDirty)

        // User backspaces to empty string -> should restore isMixed and revert dirty
        viewModel.updateField(TagField.ARTIST, "")
        val restoredArtist = viewModel.uiState.value.fields[TagField.ARTIST]
        assertEquals("", restoredArtist?.value)
        assertTrue(restoredArtist?.isMixed == true)
        assertFalse(restoredArtist?.isDirty == true)
        assertFalse(restoredArtist?.isEnabledInBatch == true)
        assertFalse(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `clearing a field marks it dirty so it gets deleted on save`() = runBlocking {
        val viewModel = createViewModel()
        viewModel.initialize(longArrayOf(1L, 2L))

        // Clear GENRE
        viewModel.removeField(TagField.GENRE)
        val genreField = viewModel.uiState.value.fields[TagField.GENRE]
        assertEquals("", genreField?.value)
        assertTrue(genreField?.isDirty == true)
        assertTrue(genreField?.isEnabledInBatch == true)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `setting and removing artwork marks artwork dirty and enabled in batch`() = runBlocking {
        val viewModel = createViewModel()
        viewModel.initialize(longArrayOf(1L, 2L))

        assertFalse(viewModel.uiState.value.isArtworkDirty)
        assertFalse(viewModel.uiState.value.isArtworkBatchEnabled)

        // New artwork
        val newBytes = byteArrayOf(1, 2, 3, 4)
        viewModel.setArtwork(newBytes, "image/png", 600, 600)
        assertTrue(viewModel.uiState.value.isArtworkDirty)
        assertTrue(viewModel.uiState.value.isArtworkBatchEnabled)
        assertFalse(viewModel.uiState.value.removeArtwork)
        assertTrue(viewModel.uiState.value.isDirty)

        // Remove artwork
        viewModel.removeArtwork()
        assertTrue(viewModel.uiState.value.isArtworkDirty)
        assertTrue(viewModel.uiState.value.isArtworkBatchEnabled)
        assertTrue(viewModel.uiState.value.removeArtwork)
        assertNull(viewModel.uiState.value.artwork)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    @Test
    fun `batch save emits exactly one NavigateBack event upon completion`() = runBlocking {
        val viewModel = createViewModel()
        viewModel.initialize(longArrayOf(1L, 2L))

        // Modify a field to make it dirty
        viewModel.updateField(TagField.ARTIST, "Brand New Artist")
        assertTrue(viewModel.uiState.value.isDirty)

        val collectedEvents = mutableListOf<EditorUiEvent>()
        val job = launch(Dispatchers.Unconfined) {
            viewModel.events.collect { collectedEvents.add(it) }
        }

        viewModel.saveMetadata()

        val navigateBackEvents = collectedEvents.filterIsInstance<EditorUiEvent.NavigateBack>()
        assertEquals(1, navigateBackEvents.size)

        val toastEvents = collectedEvents.filterIsInstance<EditorUiEvent.ShowToast>()
        assertEquals(1, toastEvents.size)
        assertEquals("All 2 tracks updated successfully", toastEvents.first().message)

        job.cancel()
    }
}
