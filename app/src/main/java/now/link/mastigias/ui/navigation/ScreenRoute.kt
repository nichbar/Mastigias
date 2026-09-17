package now.link.mastigias.ui.navigation

import kotlinx.serialization.Serializable

sealed interface ScreenRoute {
    @Serializable
    data object Library : ScreenRoute

    @Serializable
    data class Editor(val trackIds: LongArray) : ScreenRoute {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Editor) return false
            return trackIds.contentEquals(other.trackIds)
        }

        override fun hashCode(): Int = trackIds.contentHashCode()
    }

    @Serializable
    data object Settings : ScreenRoute

    @Serializable
    data object Logs : ScreenRoute

    @Serializable
    data object FolderManager : ScreenRoute
}
