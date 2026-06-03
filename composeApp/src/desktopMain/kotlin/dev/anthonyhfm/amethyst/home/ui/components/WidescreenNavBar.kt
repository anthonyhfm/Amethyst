package dev.anthonyhfm.amethyst.home.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.composables.icons.lucide.BadgeInfo
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.History
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings2
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.ui.components.primitives.LocalSidebarState
import dev.anthonyhfm.amethyst.ui.components.primitives.Sidebar
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarContent
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarGroup
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarGroupContent
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarGroupLabel
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarMenuButton
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarTrigger
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.large
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import io.github.kdroidfilter.nucleus.core.runtime.ExecutableRuntime
import org.jetbrains.compose.resources.painterResource

@Composable
fun WidescreenNavBar(
    navigator: NavHostController
) {
    val current by navigator.currentBackStackEntryAsState()
    var currentNavigation: HomeNavRoute by remember { mutableStateOf(HomeNavRoute.Recent) }
    val sidebarState = LocalSidebarState.current

    LaunchedEffect(current) {
        currentNavigation = when (current?.destination?.route) {
            HomeNavRoute.Recent::class.qualifiedName -> HomeNavRoute.Recent
            HomeNavRoute.Browser::class.qualifiedName -> HomeNavRoute.Browser
            HomeNavRoute.Settings::class.qualifiedName -> HomeNavRoute.Settings
            HomeNavRoute.Tutorials::class.qualifiedName -> HomeNavRoute.Tutorials
            HomeNavRoute.About::class.qualifiedName -> HomeNavRoute.About

            else -> currentNavigation
        }
    }

    val primaryItems = mutableListOf(NavRailItem.RECENT)

    if (ExecutableRuntime.isDev()) {
        primaryItems.add(NavRailItem.BROWSER)
    }

    val secondaryItems = listOf(
        NavRailItem.TUTORIALS,
        NavRailItem.SETTINGS,
        NavRailItem.ABOUT,
    )

    Sidebar {
        SidebarHeader {
            if (sidebarState.expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SidebarBrandMark(size = 42.dp)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Amethyst",
                                style = Theme[typography][large].copy(color = Theme[colors][foreground]),
                            )
                            Text(
                                text = "Studio Home",
                                style = Theme[typography][small].copy(color = Theme[colors][mutedForeground]),
                            )
                        }
                    }

                    SidebarTrigger()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SidebarBrandMark(size = 24.dp)
                    SidebarTrigger(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
            }
        }

        SidebarSeparator()

        SidebarContent(
            modifier = Modifier
                .padding(bottom = 12.dp),
        ) {
            SidebarGroup {
                SidebarGroupLabel("Home")
                SidebarGroupContent {
                    SidebarMenu {
                        primaryItems.forEach { item ->
                            SidebarMenuItem {
                                SidebarMenuButton(
                                    onClick = {
                                        if (currentNavigation != item.route) {
                                            navigator.navigate(item.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(navigator.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                            }
                                        }
                                    },
                                    isActive = currentNavigation == item.route,
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (currentNavigation == item.route) {
                                                Theme[colors][accentForeground]
                                            } else {
                                                Theme[colors][foreground].copy(alpha = 0.75f)
                                            },
                                        )
                                    },
                                ) {
                                    Text(item.expandedLabel)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        SidebarSeparator(modifier = Modifier.alpha(0.75f))

        SidebarFooter {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SidebarGroupLabel("Support")
                SidebarGroupContent {
                    SidebarMenu {
                        secondaryItems.forEach { item ->
                            SidebarMenuItem {
                                SidebarMenuButton(
                                    onClick = {
                                        if (currentNavigation != item.route) {
                                            navigator.navigate(item.route) {
                                                launchSingleTop = true
                                                restoreState = true
                                                popUpTo(navigator.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                            }
                                        }
                                    },
                                    isActive = currentNavigation == item.route,
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (currentNavigation == item.route) {
                                                Theme[colors][accentForeground]
                                            } else {
                                                Theme[colors][foreground].copy(alpha = 0.75f)
                                            },
                                        )
                                    },
                                ) {
                                    Text(item.expandedLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class NavRailItem(
    val label: String,
    val expandedLabel: String = label,
    val icon: ImageVector,
    val route: HomeNavRoute
) {
    companion object {
        val RECENT = NavRailItem(
            label = "Recent",
            expandedLabel = "Recent Projects",
            icon = Lucide.History,
            route = HomeNavRoute.Recent
        )

        val BROWSER = NavRailItem(
            label = "Browser",
            expandedLabel = "Project Browser",
            icon = Lucide.FolderOpen,
            route = HomeNavRoute.Browser
        )

        val SETTINGS = NavRailItem(
            label = "Settings",
            icon = Lucide.Settings2,
            route = HomeNavRoute.Settings
        )

        val TUTORIALS = NavRailItem(
            label = "Tutorials",
            expandedLabel = "Tutorials",
            icon = Lucide.BookOpen,
            route = HomeNavRoute.Tutorials
        )

        val ABOUT = NavRailItem(
            label = "About",
            expandedLabel = "About Amethyst",
            icon = Lucide.BadgeInfo,
            route = HomeNavRoute.About
        )
    }
}

@Composable
private fun SidebarBrandMark(
    size: androidx.compose.ui.unit.Dp,
) {
    Image(
        painter = painterResource(Res.drawable.amethyst_studio_logo),
        contentDescription = "Amethyst Logo",
        modifier = Modifier.size(size),
    )
}
