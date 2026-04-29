package dev.anthonyhfm.amethyst.home.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class HomeNavigationTab(
    val label: String,
    val icon: ImageVector,
    val route: HomeNavRoute,
) {
    Projects(
        label = "Projects",
        icon = Icons.Outlined.Folder,
        route = HomeNavRoute.Projects,
    ),
    Browser(
        label = "Browser",
        icon = Icons.Outlined.Search,
        route = HomeNavRoute.Browser,
    ),
    Settings(
        label = "Settings",
        icon = Icons.Outlined.Settings,
        route = HomeNavRoute.Settings,
    );

    val routeName: String?
        get() = route::class.qualifiedName

    companion object {
        fun fromRoute(route: String?): HomeNavigationTab {
            return entries.firstOrNull { it.routeName == route } ?: Projects
        }
    }
}
