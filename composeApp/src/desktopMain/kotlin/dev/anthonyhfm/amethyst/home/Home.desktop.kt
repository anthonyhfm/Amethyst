package dev.anthonyhfm.amethyst.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
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
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.home.ui.layout.AdaptiveNavigationLayout
import dev.anthonyhfm.amethyst.home.ui.views.AbletonImportWizard
import dev.anthonyhfm.amethyst.home.ui.views.AboutView
import dev.anthonyhfm.amethyst.home.ui.views.BrowserView
import dev.anthonyhfm.amethyst.home.ui.views.LoadingScreenView
import dev.anthonyhfm.amethyst.home.ui.views.ProjectCreationDialog
import dev.anthonyhfm.amethyst.home.ui.views.RecentView
import dev.anthonyhfm.amethyst.home.ui.views.SettingsView
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors

@Composable
actual fun Home(
    onOpenWorkspace: () -> Unit
) {
    val navigator = rememberNavController()
    var useWidescreenLayout: Boolean by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme[colors][background])
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
                    RecentView(
                        navigator = navigator,
                        onOpenWorkspace = {
                            onOpenWorkspace()
                        },
                    )
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

                dialog<HomeNavRoute.ProjectCreation>(
                    dialogProperties = DialogProperties(
                        usePlatformDefaultWidth = false,
                    )
                ) {
                    ProjectCreationDialog(
                        navigator = navigator,
                        openWorkspace = {
                            onOpenWorkspace()
                        }
                    )
                }

                dialog<HomeNavRoute.ProjectEdit>(
                    dialogProperties = DialogProperties(
                        usePlatformDefaultWidth = false,
                    )
                ) {
                    val route = it.toRoute<HomeNavRoute.ProjectEdit>()
                    
                    ProjectCreationDialog(
                        navigator = navigator,
                        openWorkspace = {
                            navigator.popBackStack()
                        },
                        projectPath = route.projectPath
                    )
                }

                dialog<HomeNavRoute.AbletonImportWizard>(
                    dialogProperties = DialogProperties(
                        usePlatformDefaultWidth = false,
                    )
                ) {
                    val route = it.toRoute<HomeNavRoute.AbletonImportWizard>()

                    AbletonImportWizard(
                        path = route.liveSetPath,
                        navigator = navigator,
                        onOpenWorkspace = {
                            onOpenWorkspace()
                        },
                        onCancel = {
                            navigator.popBackStack()
                        }
                    )
                }

                dialog<HomeNavRoute.LoadingScreen>(
                    dialogProperties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false,
                    )
                ) {
                    LoadingScreenView(it.toRoute<HomeNavRoute.LoadingScreen>().text)
                }
            }
        }
    }
}
