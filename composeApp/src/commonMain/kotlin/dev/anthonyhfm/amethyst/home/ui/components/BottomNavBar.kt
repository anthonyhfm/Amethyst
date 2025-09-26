package dev.anthonyhfm.amethyst.home.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons
import dev.anthonyhfm.amethyst.ui.icons.outlined.History
import dev.anthonyhfm.amethyst.ui.icons.outlined.Portal

@Composable
fun BottomNavBar(
    navigator: NavHostController
) {
    val current by navigator.currentBackStackEntryAsState()
    var currentNavigation: HomeNavRoute by remember { mutableStateOf(HomeNavRoute.Recent) }

    LaunchedEffect(current) {
        currentNavigation = when (current?.destination?.route) {
            HomeNavRoute.Recent::class.qualifiedName -> HomeNavRoute.Recent
            HomeNavRoute.Browser::class.qualifiedName -> HomeNavRoute.Browser
            HomeNavRoute.Settings::class.qualifiedName -> HomeNavRoute.Settings

            else -> HomeNavRoute.Recent
        }
    }

    val navItemMap = mapOf(
        HomeNavRoute.Recent to NavBarItem.RECENT,
        HomeNavRoute.Browser to NavBarItem.BROWSER,
        HomeNavRoute.Settings to NavBarItem.SETTINGS,
    )

    NavigationBar {
        navItemMap.forEach { (route, item) ->
            NavigationBarItem(
                selected = currentNavigation == route,
                label = {
                    Text(item.label)
                },
                icon = item.icon,
                onClick = {
                    if (currentNavigation != route) {
                        navigator.navigate(route) {
                            popUpTo(navigator.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}

private data class NavBarItem(
    val label: String,
    val icon: @Composable () -> Unit,
    val route: HomeNavRoute
) {
    companion object {
        val RECENT = NavBarItem(
            label = "Recent",
            icon = { Icon(AmethystIcons.Outlined.History, null) },
            route = HomeNavRoute.Recent
        )

        val BROWSER = NavBarItem(
            label = "Project Browser",
            icon = { Icon(AmethystIcons.Outlined.Portal, null) },
            route = HomeNavRoute.Browser
        )

        val SETTINGS = NavBarItem(
            label = "Settings",
            icon = { Icon(Icons.Default.Settings, null) },
            route = HomeNavRoute.Settings
        )
    }
}