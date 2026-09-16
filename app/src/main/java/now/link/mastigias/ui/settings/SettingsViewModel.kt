package now.link.mastigias.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import now.link.mastigias.data.media.MediaStoreDataSource
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.repository.ThemeMode
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val folderFilters: List<FolderFilter> = emptyList(),
    val hasManageMediaPermission: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val mediaStoreDataSource: MediaStoreDataSource
) : ViewModel() {

    private val _manageMediaGranted = MutableStateFlow(mediaStoreDataSource.hasManageMediaPermission())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeModeFlow,
        preferencesRepository.folderFiltersFlow,
        _manageMediaGranted
    ) { theme, filters, hasManageMedia ->
        SettingsUiState(
            themeMode = theme,
            folderFilters = filters,
            hasManageMediaPermission = hasManageMedia
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            hasManageMediaPermission = mediaStoreDataSource.hasManageMediaPermission()
        )
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun addFolderFilter(filter: FolderFilter) {
        viewModelScope.launch {
            val current = uiState.value.folderFilters.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current + filter)
        }
    }

    fun removeFolderFilter(filter: FolderFilter) {
        viewModelScope.launch {
            val current = uiState.value.folderFilters.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current)
        }
    }

    fun toggleFilterMode(filter: FolderFilter) {
        viewModelScope.launch {
            val updated = uiState.value.folderFilters.map {
                if (it.uri == filter.uri) {
                    it.copy(mode = if (it.isInclude) FilterMode.EXCLUDE else FilterMode.INCLUDE)
                } else {
                    it
                }
            }
            preferencesRepository.setFolderFilters(updated)
        }
    }

    fun refreshPermissions() {
        _manageMediaGranted.value = mediaStoreDataSource.hasManageMediaPermission()
    }
}
