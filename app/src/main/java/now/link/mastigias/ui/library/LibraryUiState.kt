package now.link.mastigias.ui.library

import now.link.mastigias.domain.model.Album
import now.link.mastigias.domain.model.Track

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val searchQuery: String = "",
    val viewMode: LibraryViewMode = LibraryViewMode.TRACKS,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE,
    val sortDirection: SortDirection = SortDirection.ASCENDING,
    val isUntaggedFilterActive: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
) {
    val isAccordionView: Boolean
        get() = viewMode == LibraryViewMode.ALBUMS

    val isEmpty: Boolean
        get() = if (isAccordionView) albums.isEmpty() else tracks.isEmpty()
}
