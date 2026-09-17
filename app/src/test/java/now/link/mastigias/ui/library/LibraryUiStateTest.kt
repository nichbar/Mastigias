package now.link.mastigias.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUiStateTest {

    @Test
    fun `isAccordionView is true only when sort is ALBUM and no search or untagged filter`() {
        val accordionState = LibraryUiState(
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "",
            isUntaggedFilterActive = false
        )
        assertTrue(accordionState.isAccordionView)

        val titleState = LibraryUiState(
            sortOrder = LibrarySortOrder.TITLE,
            searchQuery = "",
            isUntaggedFilterActive = false
        )
        assertFalse(titleState.isAccordionView)

        val artistState = LibraryUiState(
            sortOrder = LibrarySortOrder.ARTIST,
            searchQuery = "",
            isUntaggedFilterActive = false
        )
        assertFalse(artistState.isAccordionView)

        val searchState = LibraryUiState(
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "Beatles",
            isUntaggedFilterActive = false
        )
        assertFalse(searchState.isAccordionView)

        val untaggedState = LibraryUiState(
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "",
            isUntaggedFilterActive = true
        )
        assertFalse(untaggedState.isAccordionView)
    }

    @Test
    fun `isEmpty evaluates correctly based on isAccordionView`() {
        // Flat view with empty tracks
        val emptyFlatState = LibraryUiState(
            sortOrder = LibrarySortOrder.TITLE,
            tracks = emptyList()
        )
        assertTrue(emptyFlatState.isEmpty)

        // Flat view with non-empty tracks
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
            sortOrder = LibrarySortOrder.TITLE,
            tracks = listOf(sampleTrack)
        )
        assertFalse(populatedFlatState.isEmpty)

        // Accordion view with empty albums
        val emptyAccordionState = LibraryUiState(
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "",
            isUntaggedFilterActive = false,
            albums = emptyList(),
            tracks = listOf(sampleTrack) // Even if tracks has items, accordion view checks albums
        )
        assertTrue(emptyAccordionState.isEmpty)

        // Accordion view with non-empty albums
        val sampleAlbum = now.link.mastigias.domain.model.Album(
            title = "Album",
            artist = "Artist",
            tracks = listOf(sampleTrack),
            coverTrackId = 1L
        )
        val populatedAccordionState = LibraryUiState(
            sortOrder = LibrarySortOrder.ALBUM,
            searchQuery = "",
            isUntaggedFilterActive = false,
            albums = listOf(sampleAlbum)
        )
        assertFalse(populatedAccordionState.isEmpty)
    }
}
