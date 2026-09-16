package now.link.mastigias.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.FolderFilter
import now.link.mastigias.domain.repository.MusicRepository
import now.link.mastigias.domain.repository.PreferencesRepository
import now.link.mastigias.ui.library.SortDirection
import java.util.Locale
import javax.inject.Inject

class GetAlbumsUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val preferencesRepository: PreferencesRepository
) {
    operator fun invoke(
        query: String = "",
        sortDirection: SortDirection? = null,
        folderFilters: List<FolderFilter>? = null
    ): Flow<List<Album>> {
        val albumsFlow = musicRepository.observeAlbums()
        val sortDirectionFlow = sortDirection?.let { flowOf(it) } ?: preferencesRepository.sortDirectionFlow
        val filtersFlow = folderFilters?.let { flowOf(it) } ?: preferencesRepository.folderFiltersFlow

        return combine(albumsFlow, sortDirectionFlow, filtersFlow) { albums, direction, filters ->
            var result = albums

            if (filters.isNotEmpty()) {
                result = result.mapNotNull { album ->
                    val filteredTracks = album.tracks.filter { track ->
                        GetLibraryTracksUseCase.matchesFolderFilters(track.path, filters)
                    }
                    if (filteredTracks.isEmpty()) null
                    else album.copy(
                        tracks = filteredTracks,
                        coverTrackId = filteredTracks.firstOrNull { it.hasArtwork == true }?.id
                            ?: filteredTracks.firstOrNull()?.id
                    )
                }
            }

            if (query.isNotBlank()) {
                val q = query.trim()
                result = result.filter { album ->
                    album.title.contains(q, ignoreCase = true) ||
                        album.artist.contains(q, ignoreCase = true) ||
                        album.tracks.any { it.title.contains(q, ignoreCase = true) }
                }
            }

            val comparator = compareBy<Album> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.artist.lowercase(Locale.ROOT) }
            val finalComparator = if (direction == SortDirection.DESCENDING) comparator.reversed() else comparator
            result.sortedWith(finalComparator)
        }
    }
}
