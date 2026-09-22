package now.link.mastigias.ui.library

import now.link.mastigias.ui.library.components.isListScrolled
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
}
