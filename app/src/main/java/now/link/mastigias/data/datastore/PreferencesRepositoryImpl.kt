package now.link.mastigias.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import now.link.mastigias.core.logging.LogManager
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.domain.repository.ThemeMode
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.LibraryViewMode
import now.link.mastigias.ui.library.SortDirection
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.mastigiasPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mastigias_preferences"
)

@Serializable
private data class FolderFilterDto(
    val uri: String,
    val path: String,
    val mode: String
)

private fun FolderFilter.toDto(): FolderFilterDto = FolderFilterDto(
    uri = uri,
    path = path,
    mode = mode.name
)

private fun FolderFilterDto.toDomain(): FolderFilter = FolderFilter(
    uri = uri,
    path = path,
    mode = try {
        FilterMode.valueOf(mode)
    } catch (e: Exception) {
        FilterMode.INCLUDE
    }
)

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    constructor(context: Context) : this(context.mastigiasPreferencesDataStore)

    override val sortOrderFlow: Flow<LibrarySortOrder> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val value = prefs[KEY_SORT_ORDER]
            if (value != null) {
                try {
                    LibrarySortOrder.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    LibrarySortOrder.TITLE
                }
            } else {
                LibrarySortOrder.TITLE
            }
        }

    override val sortDirectionFlow: Flow<SortDirection> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val value = prefs[KEY_SORT_DIRECTION]
            if (value != null) {
                try {
                    SortDirection.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    SortDirection.ASCENDING
                }
            } else {
                SortDirection.ASCENDING
            }
        }

    override val viewModeFlow: Flow<LibraryViewMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val value = prefs[KEY_VIEW_MODE]
            if (value != null) {
                try {
                    LibraryViewMode.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    LibraryViewMode.TRACKS
                }
            } else {
                LibraryViewMode.TRACKS
            }
        }

    override val folderFiltersFlow: Flow<List<FolderFilter>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val json = prefs[KEY_FOLDER_FILTERS]
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    val dtos = Json.decodeFromString<List<FolderFilterDto>>(json)
                    dtos.map { it.toDomain() }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

    override suspend fun setSortOrder(order: LibrarySortOrder) {
        dataStore.edit { prefs ->
            prefs[KEY_SORT_ORDER] = order.name
        }
    }

    override suspend fun setSortDirection(direction: SortDirection) {
        dataStore.edit { prefs ->
            prefs[KEY_SORT_DIRECTION] = direction.name
        }
    }

    override suspend fun setViewMode(mode: LibraryViewMode) {
        dataStore.edit { prefs ->
            prefs[KEY_VIEW_MODE] = mode.name
        }
    }

    override suspend fun setFolderFilters(filters: List<FolderFilter>) {
        val dtos = filters.map { it.toDto() }
        val json = Json.encodeToString(dtos)
        dataStore.edit { prefs ->
            prefs[KEY_FOLDER_FILTERS] = json
        }
    }

    override val themeModeFlow: Flow<ThemeMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val value = prefs[KEY_THEME_MODE]
            if (value != null) {
                try {
                    ThemeMode.valueOf(value)
                } catch (e: IllegalArgumentException) {
                    ThemeMode.SYSTEM
                }
            } else {
                ThemeMode.SYSTEM
            }
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    override val loggingEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            prefs[KEY_LOGGING_ENABLED] ?: false
        }

    override suspend fun setLoggingEnabled(enabled: Boolean) {
        LogManager.setLogEnabled(enabled)
        dataStore.edit { prefs ->
            prefs[KEY_LOGGING_ENABLED] = enabled
        }
    }

    companion object {
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_SORT_DIRECTION = stringPreferencesKey("sort_direction")
        val KEY_VIEW_MODE = stringPreferencesKey("view_mode")
        val KEY_FOLDER_FILTERS = stringPreferencesKey("folder_filters")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")

        fun create(context: Context): PreferencesRepositoryImpl =
            PreferencesRepositoryImpl(context.mastigiasPreferencesDataStore)
    }
}
