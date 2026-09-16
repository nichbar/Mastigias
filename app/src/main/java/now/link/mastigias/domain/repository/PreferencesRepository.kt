package now.link.mastigias.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.SortDirection

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

interface PreferencesRepository {
    val sortOrderFlow: Flow<LibrarySortOrder>
    val sortDirectionFlow: Flow<SortDirection>
    val folderFiltersFlow: Flow<List<FolderFilter>>
    val themeModeFlow: Flow<ThemeMode> get() = flowOf(ThemeMode.SYSTEM)

    suspend fun setSortOrder(order: LibrarySortOrder)
    suspend fun setSortDirection(direction: SortDirection)
    suspend fun setFolderFilters(filters: List<FolderFilter>)
    suspend fun setThemeMode(mode: ThemeMode) {}
}
