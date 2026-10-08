package dev.anthonyhfm.amethyst.home

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.home_hub_detail_loading
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.home.account.DesktopHubAccount
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.home.ui.views.AbletonImportWizard
import dev.anthonyhfm.amethyst.home.ui.views.AboutView
import dev.anthonyhfm.amethyst.home.ui.views.ArcadeView
import dev.anthonyhfm.amethyst.home.ui.views.BrowserView
import dev.anthonyhfm.amethyst.home.ui.views.DesktopAccountView
import dev.anthonyhfm.amethyst.home.ui.views.DesktopHubDestination
import dev.anthonyhfm.amethyst.home.ui.views.DesktopHubDetail
import dev.anthonyhfm.amethyst.home.ui.views.DesktopHubDownload
import dev.anthonyhfm.amethyst.home.ui.views.DesktopHubSection
import dev.anthonyhfm.amethyst.home.ui.views.TutorialsView
import dev.anthonyhfm.amethyst.home.ui.views.LoadingScreenView
import dev.anthonyhfm.amethyst.home.ui.views.ProjectCreationDialog
import dev.anthonyhfm.amethyst.home.ui.views.RecentView
import dev.anthonyhfm.amethyst.home.ui.views.SettingsView
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.displayString
import dev.anthonyhfm.amethyst.home.ui.views.UpdateView
import androidx.compose.runtime.LaunchedEffect
import dev.anthonyhfm.amethyst.home.ui.components.WidescreenNavBar
import dev.anthonyhfm.amethyst.settings.AppLocaleRefreshBoundary
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarProvider
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberSidebarState
import dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineProjectArchiveFormat
import dev.anthonyhfm.amethyst.hub.data.HubProject
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.provider.GitHubProvider
import io.github.vinceglb.filekit.PlatformFile
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun Home(
    onOpenWorkspace: () -> Unit
) {
    val navigator = rememberNavController()
    val hubAccount = remember { DesktopHubAccount.get() }
    val hubStack = remember { mutableStateListOf<DesktopHubDestination>() }
    var hubSection by remember { mutableStateOf(DesktopHubSection.Home) }
    var useWidescreenLayout: Boolean by remember { mutableStateOf(false) }

    val updater = remember {
        NucleusUpdater {
            provider = GitHubProvider(owner = "anthonyhfm", repo = "Amethyst")
            currentVersion = amethystVersion.displayString
        }
    }
    var updateResult by remember { mutableStateOf<UpdateResult.Available?>(null) }

    LaunchedEffect(Unit) {
        val result = updater.checkForUpdates()
        if (result is UpdateResult.Available) {
            updateResult = result
            navigator.navigate(HomeNavRoute.UpdatePrompt(version = result.info.version))
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Theme[colors][background])
    ) {
        val sidebarState = rememberSidebarState(initialOpen = true)

        SidebarProvider(
            state = sidebarState,
            modifier = Modifier.fillMaxSize(),
        ) {
            AppLocaleRefreshBoundary {
                WidescreenNavBar(
                    navigator = navigator,
                    hubSection = hubSection,
                    onHubSectionChange = { section ->
                        hubStack.clear()
                        hubSection = section
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                NavHost(
                    navController = navigator,
                    startDestination = HomeNavRoute.Recent,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                ) {
                    composable<HomeNavRoute.Recent> {
                        AppLocaleRefreshBoundary {
                            RecentView(
                                navigator = navigator,
                                onNavigateHub = { destination ->
                                    hubStack.clear()
                                    hubStack.add(destination)
                                    navigator.navigate(route = HomeNavRoute.Browser)
                                },
                                onOpenWorkspace = {
                                    onOpenWorkspace()
                                },
                            )
                        }
                    }

                    composable<HomeNavRoute.Browser> {
                        AppLocaleRefreshBoundary {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                            ) {
                                BrowserView(
                                    repository = hubAccount.repository,
                                    sessionRevision = hubAccount.sessionRevision,
                                    section = hubSection,
                                    onSectionChange = {
                                        hubSection = it
                                    },
                                    onOpenArtist = {
                                        hubStack.add(DesktopHubDestination.Artist(it))
                                    },
                                    onOpenProject = { username, slug ->
                                        hubStack.add(DesktopHubDestination.Project(username, slug))
                                    },
                                    onSignIn = {
                                        navigator.navigate(HomeNavRoute.Account)
                                    },
                                )

                                hubStack.lastOrNull()?.let { destination ->
                                    DesktopHubDetail(
                                        destination = destination,
                                        repository = hubAccount.repository,
                                        onBack = {
                                            hubStack.removeAt(hubStack.lastIndex)
                                        },
                                        onNavigate = {
                                            hubStack.add(it)
                                        },
                                        onSignIn = {
                                            navigator.navigate(HomeNavRoute.Account)
                                        },
                                        onOpenDownloadedFile = { file, project ->
                                            openHubProject(file, project, navigator, onOpenWorkspace)
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Theme[colors][background]),
                                    )
                                }
                            }
                        }
                    }

                    composable<HomeNavRoute.Account> {
                        AppLocaleRefreshBoundary {
                            DesktopAccountView()
                        }
                    }

                    composable<HomeNavRoute.Arcade> {
                        AppLocaleRefreshBoundary {
                            ArcadeView(
                                onExploreHub = {
                                    hubStack.clear()
                                    hubSection = DesktopHubSection.Home
                                    navigator.navigate(HomeNavRoute.Browser) {
                                        launchSingleTop = true
                                        popUpTo(navigator.graph.findStartDestination().id)
                                    }
                                }
                            )
                        }
                    }

                    composable<HomeNavRoute.Settings> {
                        AppLocaleRefreshBoundary {
                            SettingsView()
                        }
                    }

                    composable<HomeNavRoute.Tutorials> {
                        AppLocaleRefreshBoundary {
                            TutorialsView()
                        }
                    }

                    composable<HomeNavRoute.About> {
                        AppLocaleRefreshBoundary {
                            AboutView()
                        }
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
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        )
                    ) {
                        val route = it.toRoute<HomeNavRoute.AbletonImportWizard>()

                        AbletonImportWizard(
                            path = route.liveSetPath,
                            navigator = navigator,
                            onOpenWorkspace = {
                                runCatching {
                                    DesktopHubDownload.completeImport(file = File(route.liveSetPath))
                                }.onFailure { failure ->
                                    println("Import record could not be saved: ${failure.message}")
                                }
                                onOpenWorkspace()
                            },
                            onCancel = {
                                DesktopHubDownload.discardImport(File(route.liveSetPath))
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

                    dialog<HomeNavRoute.UpdatePrompt>(
                        dialogProperties = DialogProperties(
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                            usePlatformDefaultWidth = false,
                        )
                    ) {
                        val route = it.toRoute<HomeNavRoute.UpdatePrompt>()

                        UpdateView(
                            version = route.version,
                            updater = updater,
                            updateResult = updateResult,
                            onDismiss = { navigator.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

private suspend fun openHubProject(
    file: File,
    project: HubProject,
    navigator: androidx.navigation.NavHostController,
    onOpenWorkspace: () -> Unit,
) {
    val platformFile = PlatformFile(file)
    val needsAbletonWizard = try {
        file.extension.equals("als", ignoreCase = true) ||
            (file.extension.lowercase() in setOf("zip", "rar") && withContext(context = Dispatchers.IO) {
                determineProjectArchiveFormat(file = platformFile) == ZippedProjectFormat.ABLETON
            })
    } catch (failure: Exception) {
        DesktopHubDownload.discardImport(file)
        throw failure
    }
    if (needsAbletonWizard) {
        navigator.navigate(HomeNavRoute.AbletonImportWizard(file.absolutePath))
        return
    }
    val loadingText = "${getString(Res.string.home_hub_detail_loading)} ${project.title}"
    ProjectLoadingManager.startLoading(initialStatus = loadingText)
    navigator.navigate(HomeNavRoute.LoadingScreen(loadingText))
    var opened = false
    try {
        val workspace = HomeRepository.loadWorkspaceData(platformFile)
        HomeRepository.openWorkspace(workspace, rememberRecent = true)
        opened = true
        DesktopHubDownload.completeImport(file)
        ProjectLoadingManager.finishLoading()
        navigator.popBackStack()
        onOpenWorkspace()
    } catch (failure: Exception) {
        if (!opened) DesktopHubDownload.discardImport(file)
        ProjectLoadingManager.finishLoading()
        navigator.popBackStack()
        throw failure
    }
}
