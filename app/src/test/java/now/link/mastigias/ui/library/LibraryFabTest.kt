package now.link.mastigias.ui.library

import now.link.mastigias.ui.library.components.LibraryFabMode
import now.link.mastigias.ui.library.components.isListScrolled
import now.link.mastigias.ui.library.components.resolveLibraryFabMode
import now.link.mastigias.ui.library.components.shouldShowToolbarRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFabTest {

    @Test
    fun `isListScrolled is false when at top of list`() {
        // When at top (index 0, offset 0), FAB should display as refresh
        assertFalse(isListScrolled(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0))
    }

    @Test
    fun `isListScrolled is true when first visible item has scrolled offset`() {
        // When content has scrolled even by offset, FAB should display as scroll-to-top
        assertTrue(isListScrolled(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 1))
        assertTrue(isListScrolled(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50))
    }

    @Test
    fun `isListScrolled is true when first visible item index is greater than zero`() {
        // When scrolled past item 0, FAB should display as scroll-to-top
        assertTrue(isListScrolled(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0))
        assertTrue(isListScrolled(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 20))
        assertTrue(isListScrolled(firstVisibleItemIndex = 15, firstVisibleItemScrollOffset = 100))
    }

    @Test
    fun `resolveLibraryFabMode returns EDIT when items are selected at top of list`() {
        assertEquals(
            LibraryFabMode.EDIT,
            resolveLibraryFabMode(
                hasSelection = true,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `resolveLibraryFabMode returns EDIT when items are selected even if list is scrolled`() {
        assertEquals(
            LibraryFabMode.EDIT,
            resolveLibraryFabMode(
                hasSelection = true,
                firstVisibleItemIndex = 5,
                firstVisibleItemScrollOffset = 120
            )
        )
    }

    @Test
    fun `resolveLibraryFabMode returns SCROLL_TO_TOP when no selection and list is scrolled`() {
        assertEquals(
            LibraryFabMode.SCROLL_TO_TOP,
            resolveLibraryFabMode(
                hasSelection = false,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0
            )
        )
        assertEquals(
            LibraryFabMode.SCROLL_TO_TOP,
            resolveLibraryFabMode(
                hasSelection = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 10
            )
        )
    }

    @Test
    fun `resolveLibraryFabMode returns REFRESH when no selection and list is at top`() {
        assertEquals(
            LibraryFabMode.REFRESH,
            resolveLibraryFabMode(
                hasSelection = false,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0
            )
        )
    }

    @Test
    fun `shouldShowToolbarRefresh returns false when FAB is in REFRESH mode`() {
        assertFalse(shouldShowToolbarRefresh(LibraryFabMode.REFRESH))
    }

    @Test
    fun `shouldShowToolbarRefresh returns true when FAB is in SCROLL_TO_TOP mode`() {
        assertTrue(shouldShowToolbarRefresh(LibraryFabMode.SCROLL_TO_TOP))
    }

    @Test
    fun `shouldShowToolbarRefresh returns true when FAB is in EDIT mode`() {
        assertTrue(shouldShowToolbarRefresh(LibraryFabMode.EDIT))
    }
}
