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
}
