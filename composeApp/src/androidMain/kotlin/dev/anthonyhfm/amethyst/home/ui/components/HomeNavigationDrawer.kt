package dev.anthonyhfm.amethyst.home.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.home.nav.HomeNavigationTab

@Composable
fun HomeNavigationDrawer(
    navigator: NavHostController,
    currentTab: HomeNavigationTab,
    content: @Composable () -> Unit,
) {
    val drawerItemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        unselectedContainerColor = MaterialTheme.colorScheme.background,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    PermanentNavigationDrawer(
        drawerContent = {
            PermanentDrawerSheet(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "Amethyst",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    )
                    Text(
                        text = "Studio Home",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                    )

                    HomeNavigationTab.entries.forEach { tab ->
                        NavigationDrawerItem(
                            selected = currentTab == tab,
                            onClick = {
                                navigator.navigate(tab.route) {
                                    popUpTo(navigator.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = drawerItemColors,
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(tab.label)
                            },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    ) {
        content()
    }
}
