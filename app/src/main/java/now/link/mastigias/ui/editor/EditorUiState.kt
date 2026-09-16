package now.link.mastigias.ui.editor

import android.content.IntentSender
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.AudioMetadata
import now.link.mastigias.domain.model.TagField

sealed interface EditorMode {
    data class Single(val trackId: Long) : EditorMode
    data class Batch(val trackIds: List<Long>) : EditorMode
}

data class FieldEditState(
    val isEnabledInBatch: Boolean = false,
    val value: String = "",
    val isDirty: Boolean = false
)

data class EditorUiState(
    val mode: EditorMode,
    val initialMetadata: AudioMetadata? = null,
    val fields: Map<TagField, FieldEditState> = emptyMap(),
    val artwork: ArtworkData? = null,
    val isArtworkDirty: Boolean = false,
    val removeArtwork: Boolean = false,
    val isArtworkBatchEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val saveProgress: Float = 0f,
    val pendingConsentIntent: IntentSender? = null,
    val error: String? = null
) {
    val isDirty: Boolean
        get() = isArtworkDirty || removeArtwork || fields.values.any { it.isDirty }
}

sealed interface EditorUiEvent {
    data class ShowToast(val message: String) : EditorUiEvent
    data object NavigateBack : EditorUiEvent
    data class RequestStorageConsent(val intentSender: IntentSender) : EditorUiEvent
}
