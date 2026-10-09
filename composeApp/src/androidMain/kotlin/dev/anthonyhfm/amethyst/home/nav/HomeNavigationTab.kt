package dev.anthonyhfm.amethyst.home.nav

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound

import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute

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
    Arcade(
        labelRes = Res.string.home_nav_tab_arcade,
        icon = Lucide.Gamepad2,
        route = HomeNavRoute.Arcade,
    ),
    Settings(
        labelRes = Res.string.profile_title,
        icon = Lucide.UserRound,
        route = HomeNavRoute.Settings,
    );

    val label: String @Composable get() = stringResource(labelRes)

    companion object {
        fun fromDestination(destination: NavDestination?): HomeNavigationTab {
            if (destination == null) {
                return Projects
            }

            if (
                destination.hasRoute<HomeNavRoute.ProfileAuth>() ||
                destination.hasRoute<HomeNavRoute.ProfileEdit>()
            ) {
                return Settings
            }

            if (
                destination.hasRoute<HomeNavRoute.HubLiked>() ||
                destination.hasRoute<HomeNavRoute.HubDetail>()
            ) {
                return Browser
            }

            return entries.firstOrNull { tab ->
                destination.hasRoute(route = tab.route::class)
            } ?: Projects
        }
    }
}
