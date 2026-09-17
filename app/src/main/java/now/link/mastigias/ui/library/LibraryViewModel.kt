package now.link.mastigias.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.usecase.GetAlbumsUseCase
import now.link.mastigias.domain.usecase.GetLibraryTracksUseCase
import now.link.mastigias.domain.usecase.GetTracksByAlbumUseCase
import now.link.mastigias.domain.usecase.SyncMediaStoreUseCase
import javax.inject.Inject

private data class FilterAndSortParams(
    val query: String,
    val untaggedOnly: Boolean,
    val sortOrder: LibrarySortOrder,
    val sortDirection: SortDirection
)

private data class SelectionAndStatus(
    val selectedTrackIds: Set<Long>,
    val isMultiSelectMode: Boolean,
    val isSyncing: Boolean,
    val errorMessage: String?
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryTracksUseCase: GetLibraryTracksUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val getTracksByAlbumUseCase: GetTracksByAlbumUseCase,
    private val syncMediaStoreUseCase: SyncMediaStoreUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isUntaggedFilterActive = MutableStateFlow(false)
    private val _selectedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isMultiSelectMode = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val filterAndSortParamsFlow: Flow<FilterAndSortParams> = combine(
        _searchQuery,
        _isUntaggedFilterActive,
        preferencesRepository.sortOrderFlow,
        preferencesRepository.sortDirectionFlow
    ) { query, untagged, order, direction ->
        FilterAndSortParams(query, untagged, order, direction)
    }

    private val contentFlow: Flow<Pair<List<Track>, List<Album>>> = filterAndSortParamsFlow
        .debounce { params -> if (params.query.isBlank()) 0L else 200L }
        .flatMapLatest { params ->
            if (params.sortOrder == LibrarySortOrder.ALBUM && params.query.isBlank() && !params.untaggedOnly) {
                getAlbumsUseCase(
                    query = params.query,
                    sortDirection = params.sortDirection
                ).map { albums -> emptyList<Track>() to albums }
            } else {
                getLibraryTracksUseCase(
                    query = params.query,
                    untaggedOnly = params.untaggedOnly,
                    sortOrder = params.sortOrder,
                    sortDirection = params.sortDirection
                ).map { tracks -> tracks to emptyList<Album>() }
            }
        }

    private val selectionAndStatusFlow: Flow<SelectionAndStatus> = combine(
        _selectedTrackIds,
        _isMultiSelectMode,
        _isSyncing,
        _errorMessage
    ) { selectedIds, isMultiSelect, isSyncing, error ->
        SelectionAndStatus(selectedIds, isMultiSelect, isSyncing, error)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        contentFlow,
        filterAndSortParamsFlow,
        selectionAndStatusFlow
    ) { (tracks, albums), filterParams, status ->
        LibraryUiState(
            tracks = tracks,
            albums = albums,
            searchQuery = filterParams.query,
            sortOrder = filterParams.sortOrder,
            sortDirection = filterParams.sortDirection,
            isUntaggedFilterActive = filterParams.untaggedOnly,
            selectedTrackIds = status.selectedTrackIds,
            isMultiSelectMode = status.isMultiSelectMode,
            isSyncing = status.isSyncing,
            errorMessage = status.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    init {
        // Initial sync on ViewModel startup
        sync()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    fun onSortOrderChanged(order: LibrarySortOrder) {
        viewModelScope.launch {
            preferencesRepository.setSortOrder(order)
        }
    }

    fun onSortDirectionChanged(direction: SortDirection) {
        viewModelScope.launch {
            preferencesRepository.setSortDirection(direction)
        }
    }

    fun onToggleUntaggedFilter() {
        _isUntaggedFilterActive.value = !_isUntaggedFilterActive.value
    }

    fun onTrackClicked(track: Track, onNavigateToEditor: (Long) -> Unit) {
        if (_isMultiSelectMode.value) {
            toggleTrackSelection(track.id)
        } else {
            onNavigateToEditor(track.id)
        }
    }

    fun onTrackLongClicked(track: Track) {
        if (!_isMultiSelectMode.value) {
            _isMultiSelectMode.value = true
        }
        toggleTrackSelection(track.id)
    }

    fun onAlbumHeaderLongClicked(album: Album) {
        if (!_isMultiSelectMode.value) {
            _isMultiSelectMode.value = true
        }
        val currentSelected = _selectedTrackIds.value.toMutableSet()
        val albumTrackIds = album.tracks.map { it.id }
        if (currentSelected.containsAll(albumTrackIds)) {
            currentSelected.removeAll(albumTrackIds.toSet())
        } else {
            currentSelected.addAll(albumTrackIds)
        }
        _selectedTrackIds.value = currentSelected
        if (_selectedTrackIds.value.isEmpty()) {
            _isMultiSelectMode.value = false
        }
    }

    fun editAlbum(album: Album, onNavigateToEditor: (LongArray) -> Unit) {
        val trackIds = album.tracks.map { it.id }.toLongArray()
        if (trackIds.isNotEmpty()) {
            onNavigateToEditor(trackIds)
        }
    }

    fun editAlbumForTrack(track: Track, onNavigateToEditor: (LongArray) -> Unit) {
        val albumTitle = track.album.trim()
        if (albumTitle.isBlank() || albumTitle.equals("<unknown>", ignoreCase = true) || albumTitle.equals("<unknown album>", ignoreCase = true)) {
            onNavigateToEditor(longArrayOf(track.id))
            return
        }

        viewModelScope.launch {
            val tracks = getTracksByAlbumUseCase(albumTitle, track.artist)
            val finalTracks = if (tracks.size <= 1 && track.artist.isNotBlank()) {
                getTracksByAlbumUseCase(albumTitle, null).ifEmpty { tracks }
            } else {
                tracks
            }
            val ids = if (finalTracks.isNotEmpty()) finalTracks.map { it.id }.toLongArray() else longArrayOf(track.id)
            onNavigateToEditor(ids)
        }
    }

    fun toggleTrackSelection(trackId: Long) {
        val current = _selectedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        _selectedTrackIds.value = current
        if (current.isEmpty()) {
            _isMultiSelectMode.value = false
        }
    }

    fun onSelectAll() {
        val currentState = uiState.value
        val allIds = if (currentState.isAccordionView) {
            currentState.albums.flatMap { it.tracks }.map { it.id }.toSet()
        } else {
            currentState.tracks.map { it.id }.toSet()
        }
        _selectedTrackIds.value = allIds
        if (allIds.isNotEmpty()) {
            _isMultiSelectMode.value = true
        }
    }

    fun onInvertSelection() {
        val currentState = uiState.value
        val allIds = if (currentState.isAccordionView) {
            currentState.albums.flatMap { it.tracks }.map { it.id }.toSet()
        } else {
            currentState.tracks.map { it.id }.toSet()
        }
        val currentSelected = _selectedTrackIds.value
        val inverted = allIds - currentSelected
        _selectedTrackIds.value = inverted
        if (inverted.isEmpty()) {
            _isMultiSelectMode.value = false
        } else {
            _isMultiSelectMode.value = true
        }
    }

    fun onClearSelection() {
        _selectedTrackIds.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun sync() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = syncMediaStoreUseCase()
            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to sync media store"
            }
            _isSyncing.value = false
        }
    }

    val folderFilters: StateFlow<List<FolderFilter>> = preferencesRepository.folderFiltersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFolderFilter(filter: FolderFilter) {
        viewModelScope.launch {
            val current = folderFilters.value.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current + filter)
        }
    }

    fun removeFolderFilter(filter: FolderFilter) {
        viewModelScope.launch {
            val current = folderFilters.value.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current)
        }
    }

    fun toggleFolderFilterMode(filter: FolderFilter) {
        viewModelScope.launch {
            val updated = folderFilters.value.map {
                if (it.uri == filter.uri) {
                    it.copy(mode = if (it.isInclude) FilterMode.EXCLUDE else FilterMode.INCLUDE)
                } else {
                    it
                }
            }
            preferencesRepository.setFolderFilters(updated)
        }
    }

    fun onClearError() {
        _errorMessage.value = null
    }
}
