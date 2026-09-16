package now.link.mastigias.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ScreenRouteTest {

    @Test
    fun `ScreenRoute Editor equals and hashCode based on trackIds content`() {
        val route1 = ScreenRoute.Editor(longArrayOf(1L, 2L, 3L))
        val route2 = ScreenRoute.Editor(longArrayOf(1L, 2L, 3L))
        val route3 = ScreenRoute.Editor(longArrayOf(4L, 5L))

        assertEquals(route1, route2)
        assertEquals(route1.hashCode(), route2.hashCode())
        assertNotEquals(route1, route3)
    }

    @Test
    fun `ScreenRoute singletons are equal`() {
        assertEquals(ScreenRoute.Library, ScreenRoute.Library)
        assertEquals(ScreenRoute.Settings, ScreenRoute.Settings)
        assertEquals(ScreenRoute.FolderManager, ScreenRoute.FolderManager)
    }
}
