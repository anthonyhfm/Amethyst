package dev.anthonyhfm.amethyst.home.nav

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings2

import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.Composable

enum class HomeNavigationTab(
    val labelRes: StringResource,
    val icon: ImageVector,
    val route: HomeNavRoute,
) {
    Projects(
        labelRes = Res.string.home_nav_tab_projects,
        icon = Lucide.History,
        route = HomeNavRoute.Projects,
    ),
    Browser(
        labelRes = Res.string.home_nav_tab_browser,
        icon = Lucide.FolderOpen,
        route = HomeNavRoute.Browser,
    ),
    Settings(
        labelRes = Res.string.home_nav_tab_settings,
        icon = Lucide.Settings2,
        route = HomeNavRoute.Settings,
    );

    val label: String @Composable get() = stringResource(labelRes)

    val routeName: String?
        get() = route::class.qualifiedName

    companion object {
        fun fromRoute(route: String?): HomeNavigationTab {
            return entries.firstOrNull { it.routeName == route } ?: Projects
        }
    }
}
