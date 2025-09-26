package dev.anthonyhfm.amethyst.home.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons
import dev.anthonyhfm.amethyst.ui.icons.outlined.History
import dev.anthonyhfm.amethyst.ui.icons.outlined.Portal
import org.jetbrains.compose.resources.painterResource

@Composable
fun WidescreenNavBar(
    navigator: NavHostController
) {
    val current by navigator.currentBackStackEntryAsState()
    var currentNavigation: HomeNavRoute by remember { mutableStateOf(HomeNavRoute.Recent) }

    LaunchedEffect(current) {
        currentNavigation = when (current?.destination?.route) {
            HomeNavRoute.Recent::class.qualifiedName -> HomeNavRoute.Recent
            HomeNavRoute.Browser::class.qualifiedName -> HomeNavRoute.Browser
            HomeNavRoute.Settings::class.qualifiedName -> HomeNavRoute.Settings
            HomeNavRoute.About::class.qualifiedName -> HomeNavRoute.About

            else -> HomeNavRoute.Recent
        }
    }

    val navItemMap = mapOf(
        HomeNavRoute.Recent to NavRailItem.RECENT,
        HomeNavRoute.Browser to NavRailItem.BROWSER,
        HomeNavRoute.Settings to NavRailItem.SETTINGS,
        HomeNavRoute.About to NavRailItem.ABOUT,
    )

    NavigationRail(
        header = {
            Image(
                painter = painterResource(Res.drawable.amethyst_studio_logo),
                contentDescription = "Amethyst Logo",
                modifier = Modifier
                    .size(56.dp)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 12.dp),

            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            navItemMap.forEach { (route, item) ->
                if (item == NavRailItem.SETTINGS) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                NavigationRailItem(
                    selected = currentNavigation == route,
                    onClick = {
                        navigator.navigate(item.route)
                    },
                    label = {
                        Text(item.label)
                    },
                    icon = item.icon,
                )
            }
        }
    }
}

private data class NavRailItem(
    val label: String,
    val expandedLabel: String = label,
    val icon: @Composable () -> Unit,
    val route: HomeNavRoute
) {
    companion object {
        val RECENT = NavRailItem(
            label = "Recent",
            expandedLabel = "Recent Projects",
            icon = { Icon(AmethystIcons.Outlined.History, null) },
            route = HomeNavRoute.Recent
        )

        val BROWSER = NavRailItem(
            label = "Browser",
            expandedLabel = "Project Browser",
            icon = { Icon(AmethystIcons.Outlined.Portal, null) },
            route = HomeNavRoute.Browser
        )

        val SETTINGS = NavRailItem(
            label = "Settings",
            icon = { Icon(Icons.Default.Settings, null) },
            route = HomeNavRoute.Settings
        )

        val ABOUT = NavRailItem(
            label = "About",
            expandedLabel = "About Amethyst",
            icon = { Icon(Icons.Default.Info, null) },
            route = HomeNavRoute.About
        )
    }
}