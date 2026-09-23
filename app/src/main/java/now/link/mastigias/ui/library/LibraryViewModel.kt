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
import kotlinx.coroutines.plus
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.usecase.GetAlbumsUseCase
import now.link.mastigias.domain.usecase.GetLibraryTracksUseCase
import now.link.mastigias.domain.usecase.SyncMediaStoreUseCase
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private data class FilterAndSortParams(
    val query: String,
    val untaggedOnly: Boolean,
    val viewMode: LibraryViewMode,
    val sortOrder: LibrarySortOrder,
    val sortDirection: SortDirection
)

private data class SyncStatus(
    val isSyncing: Boolean,
    val errorMessage: String?
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryTracksUseCase: GetLibraryTracksUseCase,
    private val getAlbumsUseCase: GetAlbumsUseCase,
    private val syncMediaStoreUseCase: SyncMediaStoreUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) : ViewModel() {

    companion object {
        private const val TAG = "LibraryViewModel"
    }

    private val _searchQuery = MutableStateFlow("")
    private val _isUntaggedFilterActive = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _selectedTrackIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _expandedAlbumKeys = MutableStateFlow<Set<String>>(emptySet())

    private val trackCache = ConcurrentHashMap<Long, Track>()

    private val debouncedSearchQueryFlow: Flow<String> = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else 200L }

    private val filterAndSortParamsFlow: Flow<FilterAndSortParams> = combine(
        debouncedSearchQueryFlow,
        _isUntaggedFilterActive,
        preferencesRepository.viewModeFlow,
        preferencesRepository.sortOrderFlow,
        preferencesRepository.sortDirectionFlow
    ) { query, untagged, viewMode, order, direction ->
        FilterAndSortParams(query, untagged, viewMode, order, direction)
    }

    private val contentFlow: Flow<Pair<FilterAndSortParams, Pair<List<Track>, List<Album>>>> = filterAndSortParamsFlow
        .flatMapLatest { params ->
            if (params.viewMode == LibraryViewMode.ALBUMS) {
                getAlbumsUseCase(
                    query = params.query,
                    untaggedOnly = params.untaggedOnly,
                    sortOrder = params.sortOrder,
                    sortDirection = params.sortDirection
                ).map { albums ->
                    albums.forEach { album ->
                        album.tracks.forEach { track ->
                            trackCache[track.id] = track
                        }
                    }
                    params to (emptyList<Track>() to albums)
                }
            } else {
                getLibraryTracksUseCase(
                    query = params.query,
                    untaggedOnly = params.untaggedOnly,
                    sortOrder = params.sortOrder,
                    sortDirection = params.sortDirection
                ).map { tracks ->
                    tracks.forEach { track ->
                        trackCache[track.id] = track
                    }
                    params to (tracks to emptyList<Album>())
                }
            }
        }

    private val syncStatusFlow: Flow<SyncStatus> = combine(
        _isSyncing,
        _errorMessage
    ) { isSyncing, error ->
        SyncStatus(isSyncing, error)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        _searchQuery,
        contentFlow,
        syncStatusFlow,
        _selectedTrackIds,
        _expandedAlbumKeys
    ) { currentQuery, (filterParams, content), status, selectedTrackIds, expandedAlbumKeys ->
        val (tracks, albums) = content
        LibraryUiState(
            tracks = tracks,
            albums = albums,
            searchQuery = currentQuery,
            viewMode = filterParams.viewMode,
            sortOrder = filterParams.sortOrder,
            sortDirection = filterParams.sortDirection,
            isUntaggedFilterActive = filterParams.untaggedOnly,
            isSyncing = status.isSyncing,
            errorMessage = status.errorMessage,
            selectedTrackIds = selectedTrackIds,
            expandedAlbumKeys = expandedAlbumKeys
        )
    }.stateIn(
        scope = viewModelScope + dispatchers.main,
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

    fun onViewModeChanged(mode: LibraryViewMode) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setViewMode(mode)
        }
    }

    fun onSortOrderChanged(order: LibrarySortOrder) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setSortOrder(order)
        }
    }

    fun onSortDirectionChanged(direction: SortDirection) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setSortDirection(direction)
        }
    }

    fun onToggleUntaggedFilter() {
        _isUntaggedFilterActive.value = !_isUntaggedFilterActive.value
    }

    fun toggleTrackSelection(trackId: Long) {
        val current = _selectedTrackIds.value
        _selectedTrackIds.value = if (current.contains(trackId)) {
            current - trackId
        } else {
            current + trackId
        }
    }

    fun toggleSelectAll() {
        val state = uiState.value
        val visibleTrackIds = if (state.isAccordionView) {
            state.albums.flatMap { it.tracks }.map { it.id }.toSet()
        } else {
            state.tracks.map { it.id }.toSet()
        }
        val current = _selectedTrackIds.value
        if (visibleTrackIds.isNotEmpty() && current.containsAll(visibleTrackIds)) {
            _selectedTrackIds.value = emptySet()
        } else {
            _selectedTrackIds.value = visibleTrackIds
        }
    }

    fun toggleAlbumSelection(album: Album) {
        val albumTrackIds = album.tracks.map { it.id }.toSet()
        val current = _selectedTrackIds.value
        if (current.containsAll(albumTrackIds)) {
            _selectedTrackIds.value = current - albumTrackIds
        } else {
            _selectedTrackIds.value = current + albumTrackIds
        }
    }

    fun toggleAlbumExpanded(albumKey: String) {
        val current = _expandedAlbumKeys.value
        _expandedAlbumKeys.value = if (current.contains(albumKey)) {
            current - albumKey
        } else {
            current + albumKey
        }
    }

    fun toggleAlbumExpanded(album: Album) {
        toggleAlbumExpanded(album.key)
    }

    fun collapseAllAlbums() {
        _expandedAlbumKeys.value = emptySet()
    }

    fun clearSelection() {
        _selectedTrackIds.value = emptySet()
    }

    fun isMultiAlbumSelected(): Boolean {
        val selectedIds = _selectedTrackIds.value
        if (selectedIds.size <= 1) return false

        val state = uiState.value
        val tracksFromState = if (state.isAccordionView) {
            state.albums.flatMap { it.tracks }
        } else {
            state.tracks
        }
        tracksFromState.forEach { trackCache[it.id] = it }

        val tracks = selectedIds.mapNotNull { trackCache[it] }
        val distinctAlbums = tracks.map {
            "${it.album.trim().lowercase()}:::${it.artist.trim().lowercase()}"
        }.distinct()

        return distinctAlbums.size > 1
    }

    fun editAlbum(album: Album, onNavigateToEditor: (LongArray) -> Unit) {
        val trackIds = album.tracks.map { it.id }.toLongArray()
        if (trackIds.isNotEmpty()) {
            onNavigateToEditor(trackIds)
        }
    }

    fun sync() {
        if (_isSyncing.value) return
        LogManager.d(TAG, "Triggering media store sync from library")
        viewModelScope.launch(dispatchers.io) {
            _isSyncing.value = true
            val result = syncMediaStoreUseCase()
            if (result.isFailure) {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to sync media store"
                LogManager.e(TAG, "Media store sync failed in library: $errorMsg")
                _errorMessage.value = errorMsg
            } else {
                LogManager.i(TAG, "Media store sync succeeded in library")
            }
            _isSyncing.value = false
        }
    }

    val folderFilters: StateFlow<List<FolderFilter>> = preferencesRepository.folderFiltersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFolderFilter(filter: FolderFilter) {
        viewModelScope.launch(dispatchers.io) {
            val current = folderFilters.value.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current + filter)
        }
    }

    fun removeFolderFilter(filter: FolderFilter) {
        viewModelScope.launch(dispatchers.io) {
            val current = folderFilters.value.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current)
        }
    }

    fun toggleFolderFilterMode(filter: FolderFilter) {
        viewModelScope.launch(dispatchers.io) {
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
