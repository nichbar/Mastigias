package now.link.mastigias.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.ThemeMode
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.SortDirection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PreferencesRepositoryImplTest {

    private lateinit var tempFile: File
    private lateinit var testScope: CoroutineScope
    private lateinit var repository: PreferencesRepositoryImpl

    @Before
    fun setup() {
        tempFile = File.createTempFile("test_prefs_", ".preferences_pb")
        testScope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFile }
        )
        repository = PreferencesRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun `default preferences return expected values`() = runBlocking {
        val sortOrder = repository.sortOrderFlow.first()
        val sortDirection = repository.sortDirectionFlow.first()
        val folderFilters = repository.folderFiltersFlow.first()

        assertEquals(LibrarySortOrder.TITLE, sortOrder)
        assertEquals(SortDirection.ASCENDING, sortDirection)
        assertTrue(folderFilters.isEmpty())
    }

    @Test
    fun `setSortOrder updates and persists sort order`() = runBlocking {
        repository.setSortOrder(LibrarySortOrder.ARTIST)
        assertEquals(LibrarySortOrder.ARTIST, repository.sortOrderFlow.first())

        repository.setSortOrder(LibrarySortOrder.ALBUM)
        assertEquals(LibrarySortOrder.ALBUM, repository.sortOrderFlow.first())
    }

    @Test
    fun `setSortDirection updates and persists sort direction`() = runBlocking {
        repository.setSortDirection(SortDirection.DESCENDING)
        assertEquals(SortDirection.DESCENDING, repository.sortDirectionFlow.first())

        repository.setSortDirection(SortDirection.ASCENDING)
        assertEquals(SortDirection.ASCENDING, repository.sortDirectionFlow.first())
    }

    @Test
    fun `setFolderFilters updates and persists folder filters`() = runBlocking {
        val filters = listOf(
            FolderFilter(
                uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                path = "/storage/emulated/0/Music",
                mode = FilterMode.INCLUDE
            ),
            FolderFilter(
                uri = "content://com.android.externalstorage.documents/tree/primary%3ARingtones",
                path = "/storage/emulated/0/Ringtones",
                mode = FilterMode.EXCLUDE
            )
        )

        repository.setFolderFilters(filters)

        val retrieved = repository.folderFiltersFlow.first()
        assertEquals(2, retrieved.size)

        assertEquals("content://com.android.externalstorage.documents/tree/primary%3AMusic", retrieved[0].uri)
        assertEquals("/storage/emulated/0/Music", retrieved[0].path)
        assertEquals(FilterMode.INCLUDE, retrieved[0].mode)
        assertTrue(retrieved[0].isInclude)

        assertEquals("content://com.android.externalstorage.documents/tree/primary%3ARingtones", retrieved[1].uri)
        assertEquals("/storage/emulated/0/Ringtones", retrieved[1].path)
        assertEquals(FilterMode.EXCLUDE, retrieved[1].mode)
        assertTrue(retrieved[1].isExclude)
    }

    @Test
    fun `setFolderFilters with empty list clears filters`() = runBlocking {
        val filters = listOf(
            FolderFilter(uri = "uri1", path = "/path1", mode = FilterMode.INCLUDE)
        )
        repository.setFolderFilters(filters)
        assertEquals(1, repository.folderFiltersFlow.first().size)

        repository.setFolderFilters(emptyList())
        assertTrue(repository.folderFiltersFlow.first().isEmpty())
    }

    @Test
    fun `setThemeMode updates and persists theme mode`() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, repository.themeModeFlow.first())

        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.themeModeFlow.first())

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.themeModeFlow.first())
    }
}
