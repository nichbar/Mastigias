package now.link.mastigias.ui.editor

import now.link.mastigias.domain.model.TagField
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorUiStateTest {

    @Test
    fun `isDirty is false when clean`() {
        val state = EditorUiState(
            mode = EditorMode.Single(1L),
            fields = mapOf(
                TagField.TITLE to FieldEditState(value = "Original", isDirty = false)
            ),
            isArtworkDirty = false,
            removeArtwork = false
        )
        assertFalse(state.isDirty)
    }

    @Test
    fun `isDirty is true when field is dirty`() {
        val state = EditorUiState(
            mode = EditorMode.Single(1L),
            fields = mapOf(
                TagField.TITLE to FieldEditState(value = "New Title", isDirty = true)
            ),
            isArtworkDirty = false,
            removeArtwork = false
        )
        assertTrue(state.isDirty)
    }

    @Test
    fun `isDirty is true when artwork is dirty`() {
        val state = EditorUiState(
            mode = EditorMode.Single(1L),
            fields = emptyMap(),
            isArtworkDirty = true,
            removeArtwork = false
        )
        assertTrue(state.isDirty)
    }

    @Test
    fun `isDirty is true when removeArtwork is true`() {
        val state = EditorUiState(
            mode = EditorMode.Single(1L),
            fields = emptyMap(),
            isArtworkDirty = false,
            removeArtwork = true
        )
        assertTrue(state.isDirty)
    }

    @Test
    fun `isDirty is false in batch mode with clean mixed fields`() {
        val state = EditorUiState(
            mode = EditorMode.Batch(listOf(1L, 2L)),
            fields = mapOf(
                TagField.ALBUM to FieldEditState(value = "Shared Album", isDirty = false, isMixed = false, initialValue = "Shared Album"),
                TagField.ARTIST to FieldEditState(value = "", isDirty = false, isMixed = true, initialValue = null)
            ),
            isArtworkDirty = false,
            removeArtwork = false
        )
        assertFalse(state.isDirty)
    }

    @Test
    fun `isDirty is true in batch mode when mixed field is modified`() {
        val state = EditorUiState(
            mode = EditorMode.Batch(listOf(1L, 2L)),
            fields = mapOf(
                TagField.ARTIST to FieldEditState(value = "New Artist", isDirty = true, isMixed = false, isEnabledInBatch = true)
            ),
            isArtworkDirty = false,
            removeArtwork = false
        )
        assertTrue(state.isDirty)
    }

    @Test
    fun `NavigateToBatchEditor equality and hashCode work with contentEquals`() {
        val event1 = EditorUiEvent.NavigateToBatchEditor(longArrayOf(1L, 2L, 3L))
        val event2 = EditorUiEvent.NavigateToBatchEditor(longArrayOf(1L, 2L, 3L))
        val event3 = EditorUiEvent.NavigateToBatchEditor(longArrayOf(1L, 4L))

        org.junit.Assert.assertEquals(event1, event2)
        org.junit.Assert.assertEquals(event1.hashCode(), event2.hashCode())
        org.junit.Assert.assertNotEquals(event1, event3)
    }

    @Test
    fun `isMultiAlbum defaults to false and respects assigned value`() {
        val defaultState = EditorUiState(mode = EditorMode.Batch(listOf(1L, 2L)))
        assertFalse(defaultState.isMultiAlbum)

        val multiAlbumState = EditorUiState(
            mode = EditorMode.Batch(listOf(1L, 2L)),
            isMultiAlbum = true
        )
        assertTrue(multiAlbumState.isMultiAlbum)
    }
}
