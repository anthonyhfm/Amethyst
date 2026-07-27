package dev.anthonyhfm.amethyst.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.home.nav.HomeNavigationTab
import dev.anthonyhfm.amethyst.home.ui.layout.AdaptiveHomeNavLayout
import dev.anthonyhfm.amethyst.home.ui.views.AbletonImportWizardSheet
import dev.anthonyhfm.amethyst.home.ui.views.BrowserView
import dev.anthonyhfm.amethyst.home.ui.views.LoadingScreenView
import dev.anthonyhfm.amethyst.home.ui.views.ProjectsView
import dev.anthonyhfm.amethyst.home.ui.views.SettingsView

@Composable
actual fun Home(
    onOpenWorkspace: () -> Unit,
) {
    val navigator = rememberNavController()
    val currentBackStackEntry by navigator.currentBackStackEntryAsState()
    val currentTab = HomeNavigationTab.fromRoute(currentBackStackEntry?.destination?.route)

    AdaptiveHomeNavLayout(
        navigator = navigator,
        currentTab = currentTab,
    ) {
        NavHost(
            navController = navigator,
            startDestination = HomeNavRoute.Projects,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<HomeNavRoute.Projects> {
                ProjectsView(
                    navigator = navigator,
                    onOpenWorkspace = onOpenWorkspace,
                )
            }

            composable<HomeNavRoute.Browser> {
                BrowserView()
            }

            composable<HomeNavRoute.Settings> {
                SettingsView()
            }

            dialog<HomeNavRoute.AbletonImportWizard>(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                val route = it.toRoute<HomeNavRoute.AbletonImportWizard>()
                AbletonImportWizardSheet(
                    path = route.liveSetPath,
                    navigator = navigator,
                    onOpenWorkspace = onOpenWorkspace,
                )
            }

            dialog<HomeNavRoute.LoadingScreen>(
                dialogProperties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                val route = it.toRoute<HomeNavRoute.LoadingScreen>()
                LoadingScreenView(message = route.text)
            }
        }
    }
}
