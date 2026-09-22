package now.link.mastigias.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.LibraryViewMode
import now.link.mastigias.ui.library.SortDirection

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

interface PreferencesRepository {
    val sortOrderFlow: Flow<LibrarySortOrder>
    val sortDirectionFlow: Flow<SortDirection>
    val viewModeFlow: Flow<LibraryViewMode> get() = flowOf(LibraryViewMode.TRACKS)
    val folderFiltersFlow: Flow<List<FolderFilter>>
    val themeModeFlow: Flow<ThemeMode> get() = flowOf(ThemeMode.SYSTEM)
    val loggingEnabledFlow: Flow<Boolean> get() = flowOf(false)

    suspend fun setSortOrder(order: LibrarySortOrder)
    suspend fun setSortDirection(direction: SortDirection)
    suspend fun setViewMode(mode: LibraryViewMode) {}
    suspend fun setFolderFilters(filters: List<FolderFilter>)
    suspend fun setThemeMode(mode: ThemeMode) {}
    suspend fun setLoggingEnabled(enabled: Boolean) {}
}
