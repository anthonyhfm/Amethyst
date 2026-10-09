package dev.anthonyhfm.amethyst.home

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.navigation.compose.NavHost
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.home.nav.HomeNavigationTab
import dev.anthonyhfm.amethyst.home.account.AndroidHubAccount
import dev.anthonyhfm.amethyst.home.ui.layout.AdaptiveHomeNavLayout
import dev.anthonyhfm.amethyst.home.ui.views.AbletonImportWizardSheet
import dev.anthonyhfm.amethyst.home.ui.views.ArcadeView
import dev.anthonyhfm.amethyst.home.ui.views.BrowserView
import dev.anthonyhfm.amethyst.home.ui.views.LoadingScreenView
import dev.anthonyhfm.amethyst.home.ui.views.ProjectsView
import dev.anthonyhfm.amethyst.home.ui.views.ProjectCreationSheet
import dev.anthonyhfm.amethyst.home.ui.views.SettingsView
import dev.anthonyhfm.amethyst.home.ui.views.AuthScreen
import dev.anthonyhfm.amethyst.home.ui.views.EditProfileScreen
import dev.anthonyhfm.amethyst.home.ui.views.HubDetailScreen
import dev.anthonyhfm.amethyst.home.ui.views.HubLikedScreen
import dev.anthonyhfm.amethyst.home.ui.views.HubProjectSheet
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineProjectArchiveFormat
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun Home(
    onOpenWorkspace: () -> Unit,
) {
    val navigator = rememberNavController()
    val currentBackStackEntry by navigator.currentBackStackEntryAsState()
    val currentTab = HomeNavigationTab.fromDestination(
        destination = currentBackStackEntry?.destination
    )
    var selectedProject by remember { mutableStateOf<Pair<String, String>?>(null) }
    var openError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openingText = stringResource(Res.string.home_projects_opening_project_msg)
    val openErrorText = stringResource(Res.string.home_projects_failed_open_recent_msg)

    if (openError) AlertDialog(
        onDismissRequest = { openError = false },
        title = { Text(stringResource(Res.string.home_hub_title)) },
        text = { Text(openErrorText) },
        confirmButton = { TextButton(onClick = { openError = false }) { Text(stringResource(Res.string.home_hub_dismiss)) } },
    )

    AdaptiveHomeNavLayout(
        navigator = navigator,
        currentTab = currentTab,
    ) {
        NavHost(
            navController = navigator,
            startDestination = HomeNavRoute.Projects,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable<HomeNavRoute.Projects> {
                ProjectsView(
                    navigator = navigator,
                    onOpenWorkspace = onOpenWorkspace,
                )
            }

            composable<HomeNavRoute.Browser> {
                BrowserView(navigator, onOpenProject = { username, slug -> selectedProject = username to slug })
            }

            dialog<HomeNavRoute.HubLiked>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                HubLikedScreen(
                    account = AndroidHubAccount.get(LocalContext.current),
                    onClose = { navigator.popBackStack() },
                    onSignIn = { navigator.navigate(HomeNavRoute.ProfileAuth) },
                    onOpenProject = { username, slug -> selectedProject = username to slug },
                )
            }

            dialog<HomeNavRoute.HubDetail>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                val route = it.toRoute<HomeNavRoute.HubDetail>()
                if (route.slug != null) {
                    LaunchedEffect(route.username, route.slug) {
                        navigator.popBackStack()
                        selectedProject = route.username to route.slug
                    }
                } else {
                    HubDetailScreen(
                        account = AndroidHubAccount.get(LocalContext.current),
                        username = route.username,
                        onClose = { navigator.popBackStack() },
                        onSignIn = { navigator.navigate(HomeNavRoute.ProfileAuth) },
                        onOpenProject = { username, slug -> selectedProject = username to slug },
                    )
                }
            }

            composable<HomeNavRoute.Arcade> {
                ArcadeView(onExploreHub = {
                    navigator.navigate(HomeNavRoute.Browser) {
                        popUpTo(navigator.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }

            composable<HomeNavRoute.Settings> {
                SettingsView(
                    onRequestAuth = { navigator.navigate(HomeNavRoute.ProfileAuth) },
                    onRequestEditProfile = { navigator.navigate(HomeNavRoute.ProfileEdit) },
                )
            }

            dialog<HomeNavRoute.ProfileAuth>(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                val account = AndroidHubAccount.get(LocalContext.current)
                LaunchedEffect(account.account) {
                    if (account.account != null) navigator.popBackStack()
                }
                AuthScreen(account, onDismiss = { navigator.popBackStack() })
            }

            dialog<HomeNavRoute.ProfileEdit>(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnClickOutside = false,
                ),
            ) {
                val account = AndroidHubAccount.get(LocalContext.current)
                LaunchedEffect(account.account) {
                    if (account.account == null) navigator.popBackStack()
                }
                EditProfileScreen(account, onDismiss = { navigator.popBackStack() })
            }

            dialog<HomeNavRoute.ProjectCreation>(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                ProjectCreationSheet(
                    onDismiss = { navigator.popBackStack() },
                    openWorkspace = {
                        navigator.popBackStack()
                        onOpenWorkspace()
                    },
                )
            }

            dialog<HomeNavRoute.ProjectEdit>(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                val route = it.toRoute<HomeNavRoute.ProjectEdit>()
                ProjectCreationSheet(
                    onDismiss = { navigator.popBackStack() },
                    openWorkspace = { navigator.popBackStack() },
                    projectPath = route.projectPath,
                )
            }

            dialog<HomeNavRoute.AbletonImportWizard>(
                dialogProperties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                val route = it.toRoute<HomeNavRoute.AbletonImportWizard>()
                AbletonImportWizardSheet(
                    path = route.liveSetPath,
                    navigator = navigator,
                    onOpenWorkspace = onOpenWorkspace,
                )
            }

            dialog<HomeNavRoute.LoadingScreen>(
                dialogProperties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                ),
            ) {
                val route = it.toRoute<HomeNavRoute.LoadingScreen>()
                LoadingScreenView(message = route.text)
            }
        }
    }

    selectedProject?.let { (username, slug) ->
        HubProjectSheet(
            account = AndroidHubAccount.get(LocalContext.current),
            username = username,
            slug = slug,
            onClose = { selectedProject = null },
            onSignIn = { selectedProject = null; navigator.navigate(HomeNavRoute.ProfileAuth) },
            onOpenArtist = { artist -> selectedProject = null; navigator.navigate(HomeNavRoute.HubDetail(artist, null)) },
            onDownloadedFile = { file ->
                selectedProject = null
                scope.launch {
                    var loadingShown = false
                    try {
                        val isAbleton = when (file.extension.lowercase()) {
                            "als" -> true
                            "zip", "rar" -> withContext(context = Dispatchers.IO) {
                                determineProjectArchiveFormat(file = file) == ZippedProjectFormat.ABLETON
                            }
                            else -> false
                        }
                        if (isAbleton) {
                            navigator.navigate(HomeNavRoute.AbletonImportWizard(file.path))
                        } else {
                            ProjectLoadingManager.startLoading(initialStatus = openingText)
                            navigator.navigate(HomeNavRoute.LoadingScreen(openingText))
                            loadingShown = true
                            val workspace = HomeRepository.loadWorkspaceData(file)
                            HomeRepository.openWorkspace(workspace, rememberRecent = true)
                            ProjectLoadingManager.finishLoading()
                            onOpenWorkspace()
                        }
                    } catch (error: Exception) {
                        error.printStackTrace()
                        ProjectLoadingManager.finishLoading()
                        if (loadingShown) navigator.popBackStack()
                        openError = true
                    }
                }
            },
        )
    }
}
