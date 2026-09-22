package now.link.mastigias.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUiStateTest {

    @Test
    fun `isAccordionView is true when viewMode is ALBUMS regardless of search query or sort`() {
        val albumModeState = LibraryUiState(
            viewMode = LibraryViewMode.ALBUMS,
            sortOrder = LibrarySortOrder.TITLE,
            searchQuery = "",
            isUntaggedFilterActive = false
        )
        assertTrue(albumModeState.isAccordionView)

        val albumModeWithSearch = LibraryUiState(
            viewMode = LibraryViewMode.ALBUMS,
            sortOrder = LibrarySortOrder.TITLE,
            searchQuery = "Beatles",
            isUntaggedFilterActive = false
        )
        assertTrue(albumModeWithSearch.isAccordionView)

        val tracksModeState = LibraryUiState(
            viewMode = LibraryViewMode.TRACKS,
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "",
            isUntaggedFilterActive = false
        )
        assertFalse(tracksModeState.isAccordionView)

        val tracksModeWithSearch = LibraryUiState(
            viewMode = LibraryViewMode.TRACKS,
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "Beatles",
            isUntaggedFilterActive = false
        )
        assertFalse(tracksModeWithSearch.isAccordionView)
    }

    @Test
    fun `isEmpty evaluates correctly based on isAccordionView`() {
        // Flat tracks view with empty tracks
        val emptyFlatState = LibraryUiState(
            viewMode = LibraryViewMode.TRACKS,
            sortOrder = LibrarySortOrder.TITLE,
            tracks = emptyList()
        )
        assertTrue(emptyFlatState.isEmpty)

        // Flat tracks view with non-empty tracks
        val sampleTrack = now.link.mastigias.domain.model.Track(
            id = 1L,
            path = "/storage/emulated/0/Music/test.mp3",
            title = "Test",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            durationMs = 180000L,
            hasArtwork = false,
            isTagged = true,
            dateModified = 1000L
        )
        val populatedFlatState = LibraryUiState(
            viewMode = LibraryViewMode.TRACKS,
            sortOrder = LibrarySortOrder.TITLE,
            tracks = listOf(sampleTrack)
        )
        assertFalse(populatedFlatState.isEmpty)

        // Accordion albums view with empty albums
        val emptyAccordionState = LibraryUiState(
            viewMode = LibraryViewMode.ALBUMS,
            searchQuery = "",
            isUntaggedFilterActive = false,
            albums = emptyList(),
            tracks = listOf(sampleTrack) // Even if tracks has items, accordion view checks albums
        )
        assertTrue(emptyAccordionState.isEmpty)

        // Accordion albums view with non-empty albums
        val sampleAlbum = now.link.mastigias.domain.model.Album(
            title = "Album",
            artist = "Artist",
            tracks = listOf(sampleTrack),
            coverTrackId = 1L
        )
        val populatedAccordionState = LibraryUiState(
            viewMode = LibraryViewMode.ALBUMS,
            searchQuery = "",
            isUntaggedFilterActive = false,
            albums = listOf(sampleAlbum)
        )
        assertFalse(populatedAccordionState.isEmpty)
    }

    @Test
    fun `isSelectionMode and selectedCount behave correctly based on selectedTrackIds`() {
        val emptySelectionState = LibraryUiState(
            selectedTrackIds = emptySet()
        )
        assertFalse(emptySelectionState.isSelectionMode)
        org.junit.Assert.assertEquals(0, emptySelectionState.selectedCount)

        val activeSelectionState = LibraryUiState(
            selectedTrackIds = setOf(10L, 20L, 30L)
        )
        assertTrue(activeSelectionState.isSelectionMode)
        org.junit.Assert.assertEquals(3, activeSelectionState.selectedCount)
    }
}
