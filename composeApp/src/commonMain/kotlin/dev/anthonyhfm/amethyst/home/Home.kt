package dev.anthonyhfm.amethyst.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.home.ui.layout.AdaptiveNavigationLayout
import dev.anthonyhfm.amethyst.home.ui.views.AboutView
import dev.anthonyhfm.amethyst.home.ui.views.BrowserView
import dev.anthonyhfm.amethyst.home.ui.views.RecentView
import dev.anthonyhfm.amethyst.home.ui.views.SettingsView

@Composable
fun Home() {
    val navigator = rememberNavController()
    var useWidescreenLayout: Boolean by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        useWidescreenLayout = maxWidth > 700.dp

        AdaptiveNavigationLayout(
            navigator = navigator,
            isWidescreen = useWidescreenLayout,
        ) {
            NavHost(
                navController = navigator,
                startDestination = HomeNavRoute.Recent,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable<HomeNavRoute.Recent> {
                    RecentView()
                }

                composable<HomeNavRoute.Browser> {
                    BrowserView()
                }

                composable<HomeNavRoute.Settings> {
                    SettingsView()
                }

                composable<HomeNavRoute.About> {
                    AboutView()
                }
            }
        }
    }
}