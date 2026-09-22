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
import kotlinx.coroutines.plus
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.media.MediaStoreDataSource
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.repository.ThemeMode
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val folderFilters: List<FolderFilter> = emptyList(),
    val hasManageMediaPermission: Boolean = false,
    val isLoggingEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val dispatchers: AppDispatchers = AppDispatchers()
) : ViewModel() {

    private val _manageMediaGranted = MutableStateFlow(mediaStoreDataSource.hasManageMediaPermission())

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeModeFlow,
        preferencesRepository.folderFiltersFlow,
        _manageMediaGranted,
        preferencesRepository.loggingEnabledFlow
    ) { theme, filters, hasManageMedia, loggingEnabled ->
        SettingsUiState(
            themeMode = theme,
            folderFilters = filters,
            hasManageMediaPermission = hasManageMedia,
            isLoggingEnabled = loggingEnabled
        )
    }.stateIn(
        scope = viewModelScope + dispatchers.main,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            hasManageMediaPermission = mediaStoreDataSource.hasManageMediaPermission()
        )
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch(dispatchers.io) {
            preferencesRepository.setLoggingEnabled(enabled)
        }
    }

    fun addFolderFilter(filter: FolderFilter) {
        viewModelScope.launch(dispatchers.io) {
            val current = uiState.value.folderFilters.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current + filter)
        }
    }

    fun removeFolderFilter(filter: FolderFilter) {
        viewModelScope.launch(dispatchers.io) {
            val current = uiState.value.folderFilters.filter { it.uri != filter.uri }
            preferencesRepository.setFolderFilters(current)
        }
    }

    fun toggleFilterMode(filter: FolderFilter) {
        viewModelScope.launch(dispatchers.io) {
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
