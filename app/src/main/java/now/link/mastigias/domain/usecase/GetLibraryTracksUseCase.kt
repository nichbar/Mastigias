package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import now.link.mastigias.domain.model.FilterMode
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.model.Track
import now.link.mastigias.domain.repository.MusicRepository
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.ui.library.LibrarySortOrder
import now.link.mastigias.ui.library.SortDirection
import java.util.Locale
import javax.inject.Inject

class GetLibraryTracksUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(
        query: String = "",
        untaggedOnly: Boolean = false,
        sortOrder: LibrarySortOrder? = null,
        sortDirection: SortDirection? = null,
        folderFilters: List<FolderFilter>? = null
    ): Flow<List<Track>> {
        val tracksFlow = when {
            query.isNotBlank() -> musicRepository.searchTracks(query)
            untaggedOnly -> musicRepository.observeUntaggedTracks()
            else -> musicRepository.observeTracks()
        }

        val sortOrderFlow = sortOrder?.let { flowOf(it) } ?: preferencesRepository.sortOrderFlow
        val sortDirectionFlow = sortDirection?.let { flowOf(it) } ?: preferencesRepository.sortDirectionFlow
        val filtersFlow = folderFilters?.let { flowOf(it) } ?: preferencesRepository.folderFiltersFlow

        return combine(tracksFlow, filtersFlow, sortOrderFlow, sortDirectionFlow) { tracks, filters, order, direction ->
            var result = tracks

            if (untaggedOnly) {
                result = result.filter { !it.isTagged }
            }

            if (filters.isNotEmpty()) {
                result = result.filter { track -> matchesFolderFilters(track.path, filters) }
            }

            val comparator = getComparator(order)
            val finalComparator = if (direction == SortDirection.DESCENDING) comparator.reversed() else comparator
            result.sortedWith(finalComparator)
        }
    }

    private fun getComparator(order: LibrarySortOrder): Comparator<Track> = when (order) {
        LibrarySortOrder.TITLE -> compareBy<Track> { it.title.lowercase(Locale.ROOT) }
            .thenBy { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.trackNumber }
        LibrarySortOrder.ARTIST -> compareBy<Track> { it.artist.lowercase(Locale.ROOT) }
            .thenBy { it.album.lowercase(Locale.ROOT) }
            .thenBy { it.trackNumber }
            .thenBy { it.title.lowercase(Locale.ROOT) }
        LibrarySortOrder.ALBUM -> compareBy<Track> { it.album.lowercase(Locale.ROOT) }
            .thenBy { it.trackNumber }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    }

    companion object {
        fun matchesFolderFilters(trackPath: String, filters: List<FolderFilter>): Boolean {
            if (filters.isEmpty()) return true

            val includeFilters = filters.filter { it.mode == FilterMode.INCLUDE }
            val excludeFilters = filters.filter { it.mode == FilterMode.EXCLUDE }

            fun isUnder(folder: String): Boolean {
                val normalized = folder.trimEnd('/')
                return trackPath == normalized || trackPath.startsWith("$normalized/")
            }

            if (includeFilters.isNotEmpty()) {
                if (includeFilters.none { isUnder(it.path) }) {
                    return false
                }
            }

            if (excludeFilters.isNotEmpty()) {
                if (excludeFilters.any { isUnder(it.path) }) {
                    return false
                }
            }

            return true
        }
    }
}
