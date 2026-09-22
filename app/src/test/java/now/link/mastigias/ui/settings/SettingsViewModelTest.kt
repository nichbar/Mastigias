package now.link.mastigias.ui.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import now.link.mastigias.core.common.AppDispatchers
import now.link.mastigias.data.media.MediaStoreDataSource
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.repository.ThemeMode
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.LibraryViewMode
import now.link.mastigias.ui.library.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakePreferencesRepository : PreferencesRepository {
    override val sortOrderFlow = MutableStateFlow(LibrarySortOrder.TITLE)
    override val sortDirectionFlow = MutableStateFlow(SortDirection.ASCENDING)
    override val viewModeFlow = MutableStateFlow(LibraryViewMode.TRACKS)
    override val folderFiltersFlow = MutableStateFlow<List<FolderFilter>>(emptyList())
    override val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    override val loggingEnabledFlow = MutableStateFlow(false)

    override suspend fun setSortOrder(order: LibrarySortOrder) {
        sortOrderFlow.value = order
    }

    override suspend fun setSortDirection(direction: SortDirection) {
        sortDirectionFlow.value = direction
    }

    override suspend fun setViewMode(mode: LibraryViewMode) {
        viewModeFlow.value = mode
    }

    override suspend fun setFolderFilters(filters: List<FolderFilter>) {
        folderFiltersFlow.value = filters
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
    }

    override suspend fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabledFlow.value = enabled
    }
}

class SettingsViewModelTest {

    private val testDispatchers = AppDispatchers(
        default = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
        unconfined = Dispatchers.Unconfined
    )

    private lateinit var fakeRepo: FakePreferencesRepository
    private lateinit var mediaStoreDataSource: MediaStoreDataSource

    @Before
    fun setUp() {
        fakeRepo = FakePreferencesRepository()
        mediaStoreDataSource = MediaStoreDataSource()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            preferencesRepository = fakeRepo,
            mediaStoreDataSource = mediaStoreDataSource,
            dispatchers = testDispatchers
        )
    }

    @Test
    fun `SettingsUiState defaults isLoggingEnabled to false`() {
        val defaultState = SettingsUiState()
        assertFalse(defaultState.isLoggingEnabled)
        assertEquals(ThemeMode.SYSTEM, defaultState.themeMode)
        assertTrue(defaultState.folderFilters.isEmpty())
        assertFalse(defaultState.hasManageMediaPermission)
    }

    @Test
    fun `PreferencesRepository default interface method provides loggingEnabledFlow with false`() = runBlocking {
        val repo = object : PreferencesRepository {
            override val sortOrderFlow = MutableStateFlow(LibrarySortOrder.TITLE)
            override val sortDirectionFlow = MutableStateFlow(SortDirection.ASCENDING)
            override val folderFiltersFlow = MutableStateFlow<List<FolderFilter>>(emptyList())
            override suspend fun setSortOrder(order: LibrarySortOrder) {}
            override suspend fun setSortDirection(direction: SortDirection) {}
            override suspend fun setFolderFilters(filters: List<FolderFilter>) {}
        }
        assertFalse(repo.loggingEnabledFlow.first())
    }

    @Test
    fun `SettingsViewModel initializes with logging disabled`() = runBlocking {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.isLoggingEnabled)
    }

    @Test
    fun `SettingsViewModel setLoggingEnabled updates repository and uiState`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        assertFalse(viewModel.uiState.value.isLoggingEnabled)

        viewModel.setLoggingEnabled(true)
        assertTrue(fakeRepo.loggingEnabledFlow.first())
        assertTrue(viewModel.uiState.value.isLoggingEnabled)

        viewModel.setLoggingEnabled(false)
        assertFalse(fakeRepo.loggingEnabledFlow.first())
        assertFalse(viewModel.uiState.value.isLoggingEnabled)

        job.cancel()
    }

    @Test
    fun `SettingsViewModel setThemeMode updates theme`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)

        viewModel.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)

        viewModel.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)

        job.cancel()
    }

    @Test
    fun `SettingsViewModel folder filter operations update state`() = runBlocking {
        val viewModel = createViewModel()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect {} }

        val filter = FolderFilter(
            uri = "content://media/folder1",
            path = "/storage/Music",
            mode = FilterMode.INCLUDE
        )

        viewModel.addFolderFilter(filter)
        assertEquals(1, viewModel.uiState.value.folderFilters.size)
        assertTrue(viewModel.uiState.value.folderFilters[0].isInclude)

        viewModel.toggleFilterMode(filter)
        assertEquals(FilterMode.EXCLUDE, viewModel.uiState.value.folderFilters[0].mode)

        viewModel.removeFolderFilter(filter)
        assertTrue(viewModel.uiState.value.folderFilters.isEmpty())

        job.cancel()
    }
}
