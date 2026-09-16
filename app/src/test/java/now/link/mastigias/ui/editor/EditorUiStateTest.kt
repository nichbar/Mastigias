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
}
