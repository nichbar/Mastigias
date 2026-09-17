package now.link.mastigias.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import now.link.mastigias.ui.editor.EditorScreen
import now.link.mastigias.ui.editor.EditorViewModel
import now.link.mastigias.ui.library.LibraryScreen
import now.link.mastigias.ui.library.LibraryViewModel
import now.link.mastigias.ui.settings.SettingsScreen
import now.link.mastigias.ui.settings.SettingsViewModel

@Composable
fun MastigiasNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = ScreenRoute.Library,
        modifier = modifier
    ) {
        composable<ScreenRoute.Library> {
            val viewModel: LibraryViewModel = hiltViewModel()
            LibraryScreen(
                viewModel = viewModel,
                onNavigateToEditor = { trackIds ->
                    navController.navigate(ScreenRoute.Editor(trackIds))
                },
                onNavigateToSettings = {
                    navController.navigate(ScreenRoute.Settings)
                },
                onNavigateToFolderManager = {
                    navController.navigate(ScreenRoute.FolderManager)
                }
            )
        }

        composable<ScreenRoute.Editor> { backStackEntry ->
            val route = backStackEntry.toRoute<ScreenRoute.Editor>()
            val viewModel: EditorViewModel = hiltViewModel()

            LaunchedEffect(route.trackIds) {
                viewModel.initialize(route.trackIds)
            }

            EditorScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToBatchEditor = { trackIds ->
                    navController.navigate(ScreenRoute.Editor(trackIds))
                }
            )
        }

        composable<ScreenRoute.Settings> {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable<ScreenRoute.FolderManager> {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
