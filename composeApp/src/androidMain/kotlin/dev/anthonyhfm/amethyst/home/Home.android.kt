package dev.anthonyhfm.amethyst.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.home.nav.HomeNavigationTab
import dev.anthonyhfm.amethyst.home.ui.components.HomeBottomNavBar
import dev.anthonyhfm.amethyst.home.ui.views.BrowserView
import dev.anthonyhfm.amethyst.home.ui.views.ProjectsView
import dev.anthonyhfm.amethyst.home.ui.views.SettingsView

@Composable
actual fun Home(
    onOpenWorkspace: () -> Unit,
) {
    val navigator = rememberNavController()
    val currentBackStackEntry by navigator.currentBackStackEntryAsState()
    val currentTab = HomeNavigationTab.fromRoute(currentBackStackEntry?.destination?.route)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            HomeBottomNavBar(
                navigator = navigator,
                currentTab = currentTab,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navigator,
            startDestination = HomeNavRoute.Projects,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<HomeNavRoute.Projects> {
                ProjectsView()
            }

            composable<HomeNavRoute.Browser> {
                BrowserView()
            }

            composable<HomeNavRoute.Settings> {
                SettingsView()
            }
        }
    }
}
