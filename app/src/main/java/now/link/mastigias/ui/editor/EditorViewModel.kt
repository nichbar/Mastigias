package now.link.mastigias.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.data.media.MediaStoreDataSource
import now.link.mastigias.domain.model.ArtworkData
import now.link.mastigias.domain.model.TagCategory
import now.link.mastigias.domain.model.TagField
import now.link.mastigias.domain.usecase.BatchWriteMetadataUseCase
import now.link.mastigias.domain.usecase.GetTracksByAlbumUseCase
import now.link.mastigias.domain.usecase.ReadTrackMetadataUseCase
import now.link.mastigias.domain.usecase.WriteTrackMetadataUseCase
import now.link.mastigias.ui.navigation.ScreenRoute
import javax.inject.Inject

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val readTrackMetadataUseCase: ReadTrackMetadataUseCase,
    private val writeTrackMetadataUseCase: WriteTrackMetadataUseCase,
    private val batchWriteMetadataUseCase: BatchWriteMetadataUseCase,
    private val getTracksByAlbumUseCase: GetTracksByAlbumUseCase,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val dispatchers: AppDispatchers = AppDispatchers()
) : ViewModel() {

    companion object {
        private const val TAG = "EditorViewModel"
    }

    private val _uiState = MutableStateFlow(
        EditorUiState(
            mode = EditorMode.Single(0L)
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<EditorUiEvent>()
    val events: SharedFlow<EditorUiEvent> = _events.asSharedFlow()

    private var initializedTrackIds: LongArray? = null

    init {
        val route = runCatching { savedStateHandle.toRoute<ScreenRoute.Editor>() }.getOrNull()
        val routeIds = route?.trackIds ?: savedStateHandle.get<LongArray>("trackIds")
        if (routeIds != null && routeIds.isNotEmpty()) {
            initialize(routeIds)
        }
    }

    fun initialize(trackIds: LongArray) {
        if (trackIds.isEmpty()) return
        if (initializedTrackIds?.contentEquals(trackIds) == true) {
            return
        }
        initializedTrackIds = trackIds.clone()
        LogManager.d(TAG, "Initializing editor with ${trackIds.size} track(s): ${trackIds.joinToString()}")

        if (trackIds.size == 1) {
            val trackId = trackIds[0]
            _uiState.value = EditorUiState(
                mode = EditorMode.Single(trackId)
            )
            loadSingleTrackMetadata(trackId)
        } else {
            val idsList = trackIds.toList()
            val initialFields = TagField.basicFields
                .filter { it.category != TagCategory.LYRICS }
                .associateWith {
                    FieldEditState(
                        isEnabledInBatch = false,
                        value = "",
                        isDirty = false
                    )
                }

            _uiState.value = EditorUiState(
                mode = EditorMode.Batch(idsList),
                fields = initialFields
            )
        }
    }

    private fun loadSingleTrackMetadata(trackId: Long) {
        viewModelScope.launch(dispatchers.io) {
            LogManager.d(TAG, "Loading metadata for single track $trackId")
            val result = readTrackMetadataUseCase(trackId)
            if (result.isSuccess) {
                val metadata = result.getOrThrow()
                LogManager.d(TAG, "Loaded metadata for track $trackId: ${metadata.fields.size} fields, artwork=${metadata.artwork != null}")
                val fieldMap = mutableMapOf<TagField, FieldEditState>()

                // Add all basic fields
                TagField.basicFields.forEach { field ->
                    fieldMap[field] = FieldEditState(
                        value = metadata.fields[field] ?: "",
                        isDirty = false
                    )
                }

                // Add any additional non-basic fields that have values in metadata
                metadata.fields.forEach { (field, value) ->
                    if (!fieldMap.containsKey(field) && value.isNotBlank()) {
                        fieldMap[field] = FieldEditState(
                            value = value,
                            isDirty = false
                        )
                    }
                }

                _uiState.value = _uiState.value.copy(
                    initialMetadata = metadata,
                    fields = fieldMap,
                    artwork = metadata.artwork,
                    isArtworkDirty = false,
                    removeArtwork = false
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to read track metadata"
                LogManager.e(TAG, "Failed to load metadata for track $trackId: $errorMsg")
                _uiState.value = _uiState.value.copy(
                    error = errorMsg
                )
            }
        }
    }

    fun updateField(field: TagField, value: String) {
        val currentFields = _uiState.value.fields.toMutableMap()
        val currentEdit = currentFields[field] ?: FieldEditState()
        val initialValue = _uiState.value.initialMetadata?.fields?.get(field) ?: ""
        val isDirty = if (_uiState.value.mode is EditorMode.Single) {
            value != initialValue
        } else {
            value.isNotEmpty()
        }

        currentFields[field] = currentEdit.copy(
            value = value,
            isDirty = isDirty
        )
        _uiState.value = _uiState.value.copy(fields = currentFields)
    }

    fun toggleFieldBatch(field: TagField, isEnabled: Boolean) {
        val currentFields = _uiState.value.fields.toMutableMap()
        val currentEdit = currentFields[field] ?: FieldEditState()
        currentFields[field] = currentEdit.copy(
            isEnabledInBatch = isEnabled,
            isDirty = isEnabled
        )
        _uiState.value = _uiState.value.copy(fields = currentFields)
    }

    fun removeField(field: TagField) {
        val currentFields = _uiState.value.fields.toMutableMap()
        if (TagField.basicFields.contains(field)) {
            // Reset basic field value to blank
            val currentEdit = currentFields[field] ?: FieldEditState()
            currentFields[field] = currentEdit.copy(
                value = "",
                isDirty = true
            )
        } else {
            // Remove custom/advanced field
            currentFields.remove(field)
        }
        _uiState.value = _uiState.value.copy(fields = currentFields)
    }

    fun addField(field: TagField) {
        val currentFields = _uiState.value.fields.toMutableMap()
        if (!currentFields.containsKey(field)) {
            val isBatch = _uiState.value.mode is EditorMode.Batch
            currentFields[field] = FieldEditState(
                isEnabledInBatch = isBatch,
                value = "",
                isDirty = isBatch
            )
            _uiState.value = _uiState.value.copy(fields = currentFields)
        }
    }

    fun setArtwork(bytes: ByteArray, mimeType: String, width: Int = 0, height: Int = 0) {
        val artworkData = ArtworkData(bytes, mimeType, width, height)
        _uiState.value = _uiState.value.copy(
            artwork = artworkData,
            isArtworkDirty = true,
            removeArtwork = false
        )
    }

    fun removeArtwork() {
        _uiState.value = _uiState.value.copy(
            artwork = null,
            removeArtwork = true,
            isArtworkDirty = true
        )
    }

    fun onEditAlbumClicked() {
        val state = _uiState.value
        if (state.mode !is EditorMode.Single) return

        val albumTitle = state.fields[TagField.ALBUM]?.value?.trim()
            ?: state.initialMetadata?.fields?.get(TagField.ALBUM)?.trim()
            ?: ""

        if (albumTitle.isBlank() || albumTitle.equals("<unknown>", ignoreCase = true) || albumTitle.equals("<unknown album>", ignoreCase = true)) {
            viewModelScope.launch {
                _events.emit(EditorUiEvent.ShowToast("Cannot find songs for an unknown album"))
            }
            return
        }

        val artistName = state.fields[TagField.ARTIST]?.value?.trim()
            ?: state.initialMetadata?.fields?.get(TagField.ARTIST)?.trim()

        viewModelScope.launch(dispatchers.io) {
            val tracks = getTracksByAlbumUseCase(albumTitle, artistName)
            val finalTracks = if (tracks.size <= 1 && artistName != null) {
                getTracksByAlbumUseCase(albumTitle, null).ifEmpty { tracks }
            } else {
                tracks
            }

            if (finalTracks.size <= 1) {
                _events.emit(EditorUiEvent.ShowToast("No other tracks found in this album"))
            } else {
                _events.emit(EditorUiEvent.NavigateToBatchEditor(finalTracks.map { it.id }.toLongArray()))
            }
        }
    }

    fun toggleArtworkBatch(isEnabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isArtworkBatchEnabled = isEnabled
        )
    }

    fun saveMetadata() {
        val state = _uiState.value
        val trackIds = when (val mode = state.mode) {
            is EditorMode.Single -> listOf(mode.trackId)
            is EditorMode.Batch -> mode.trackIds
        }

        // Check if Android 11+ Scoped Storage requires write consent dialog
        val consentIntent = mediaStoreDataSource.createBatchWriteRequest(trackIds)
        if (consentIntent != null) {
            _uiState.value = _uiState.value.copy(pendingConsentIntent = consentIntent)
            viewModelScope.launch {
                _events.emit(EditorUiEvent.RequestStorageConsent(consentIntent))
            }
            return
        }

        executeSave()
    }

    fun onConsentGranted() {
        _uiState.value = _uiState.value.copy(pendingConsentIntent = null)
        executeSave()
    }

    fun onConsentDenied() {
        _uiState.value = _uiState.value.copy(pendingConsentIntent = null)
        viewModelScope.launch {
            _events.emit(EditorUiEvent.ShowToast("Storage permission denied. Changes not saved."))
        }
    }

    private fun executeSave() {
        val state = _uiState.value
        when (val mode = state.mode) {
            is EditorMode.Single -> executeSingleSave(mode.trackId)
            is EditorMode.Batch -> executeBatchSave(mode.trackIds)
        }
    }

    private fun executeSingleSave(trackId: Long) {
        val state = _uiState.value
        _uiState.value = _uiState.value.copy(isSaving = true, error = null)

        viewModelScope.launch(dispatchers.io) {
            val updatedFields = mutableMapOf<TagField, String>()
            val deletedFields = mutableSetOf<TagField>()

            for ((field, editState) in state.fields) {
                if (editState.isDirty) {
                    if (editState.value.isBlank()) {
                        deletedFields.add(field)
                    } else {
                        updatedFields[field] = editState.value
                    }
                }
            }

            val result = writeTrackMetadataUseCase(
                trackId = trackId,
                updatedFields = updatedFields,
                deletedFields = deletedFields,
                updatedArtwork = if (state.isArtworkDirty && !state.removeArtwork) state.artwork else null,
                removeArtwork = state.removeArtwork
            )

            _uiState.value = _uiState.value.copy(isSaving = false)

            if (result.isSuccess) {
                LogManager.i(TAG, "Single track $trackId tags saved successfully")
                _events.emit(EditorUiEvent.ShowToast("Tags saved successfully"))
                _events.emit(EditorUiEvent.NavigateBack)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to save track tags"
                LogManager.e(TAG, "Failed to save tags for track $trackId: $errorMsg")
                _uiState.value = _uiState.value.copy(error = errorMsg)
                _events.emit(EditorUiEvent.ShowToast("Save failed: $errorMsg"))
            }
        }
    }

    private fun executeBatchSave(trackIds: List<Long>) {
        val state = _uiState.value
        _uiState.value = _uiState.value.copy(isSaving = true, saveProgress = 0f, error = null)

        viewModelScope.launch(dispatchers.io) {
            batchWriteMetadataUseCase(
                trackIds = trackIds,
                fieldEdits = state.fields,
                artworkData = state.artwork,
                isArtworkBatchEnabled = state.isArtworkBatchEnabled,
                removeArtwork = state.removeArtwork
            ).collect { progress ->
                val progressFraction = if (progress.total > 0) {
                    progress.current.toFloat() / progress.total.toFloat()
                } else {
                    1f
                }
                _uiState.value = _uiState.value.copy(saveProgress = progressFraction)

                if (progress.current == progress.total) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    val succeeded = progress.total - progress.failedIds.size
                    LogManager.i(TAG, "Batch save completed: $succeeded succeeded, ${progress.failedIds.size} failed out of ${progress.total}")
                    if (progress.failedIds.isEmpty()) {
                        _events.emit(EditorUiEvent.ShowToast("All ${progress.total} tracks updated successfully"))
                    } else {
                        _events.emit(EditorUiEvent.ShowToast("$succeeded saved, ${progress.failedIds.size} failed"))
                    }
                    _events.emit(EditorUiEvent.NavigateBack)
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
