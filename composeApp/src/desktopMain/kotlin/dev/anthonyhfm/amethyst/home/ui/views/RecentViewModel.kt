package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch

class RecentViewModel(
    private val navigator: NavHostController,
    private val snackbarHostState: SnackbarHostState
) : BaseViewModel<Nothing?, RecentViewContract.Event, RecentViewContract.Effect>(null) {
    override fun onEvent(event: RecentViewContract.Event) {
        when (event) {
            is RecentViewContract.Event.OnClickOpenProject -> {
                viewModelScope.launch {
                    val file = FileKit.openFilePicker(
                        type = FileKitType.File(
                            extensions = mutableListOf("ame", "als", "zip").apply {
                                if (GlobalSettings.experimentalApolloConversionSupport) {
                                    add("approj")
                                }
                            }
                        ),
                        title = "Open Project File"
                    )

                    if (file == null) return@launch

                    when (file.extension.lowercase()) {
                        "ame" -> { // Native Amethyst Projects
                            runWorkspaceLoad(
                                loadingText = "Loading Project",
                                errorMessage = "Invalid Amethyst Project File",
                            ) {
                                val workspace = HomeRepository.loadWorkspaceData(file)
                                HomeRepository.openWorkspace(workspace)
                            }
                        }

                        "als" -> { // Ableton Live-Sets
                            navigator.navigate(HomeNavRoute.AbletonImportWizard(file.absolutePath()))
                        }

                        "approj" -> { // Apollo Projects
                            runWorkspaceLoad(
                                loadingText = "Translating your Apollo Project",
                                errorMessage = "Failed to convert Apollo Project",
                                printStackTrace = true,
                            ) {
                                val workspace = HomeRepository.loadWorkspaceData(file)
                                HomeRepository.openWorkspace(workspace)
                            }
                        }

                        "zip" -> {
                            val format = Zip.determineFormat(file)

                            when (format) {
                                ZippedProjectFormat.ABLETON -> {
                                    navigator.navigate(HomeNavRoute.AbletonImportWizard(file.path))
                                }

                                ZippedProjectFormat.ABLETON_APOLLO -> {
                                    runWorkspaceLoad(
                                        loadingText = "Translating your Ableton + Apollo Project",
                                        errorMessage = "Failed to convert Ableton + Apollo Project",
                                        printStackTrace = true,
                                    ) {
                                        val workspace = HomeRepository.loadWorkspaceData(file)
                                        HomeRepository.openWorkspace(workspace)
                                    }
                                }

                                ZippedProjectFormat.UNIPAD -> {
                                    runWorkspaceLoad(
                                        loadingText = "Translating your UniPad Project",
                                        errorMessage = "Failed to convert UniPad Project",
                                    ) {
                                        val workspace = HomeRepository.loadWorkspaceData(file)
                                        HomeRepository.openWorkspace(workspace)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is RecentViewContract.Event.OnClickNewProject -> {
                navigator.navigate(HomeNavRoute.ProjectCreation)
            }

            is RecentViewContract.Event.OpenProjectFromHistory -> {
                viewModelScope.launch {
                    runWorkspaceLoad(
                        errorMessage = "Failed to open recent project",
                        printStackTrace = true,
                    ) {
                        HomeRepository.openRecentWorkspace(event.project)
                    }
                }
            }
            
            is RecentViewContract.Event.OnClickEditProject -> {
                navigator.navigate(HomeNavRoute.ProjectEdit(projectPath = event.project.path))
            }
        }
    }

    private suspend fun runWorkspaceLoad(
        loadingText: String? = null,
        errorMessage: String,
        printStackTrace: Boolean = false,
        block: suspend () -> Unit,
    ) {
        if (loadingText != null) {
            navigator.navigate(HomeNavRoute.LoadingScreen(loadingText))
        }

        try {
            block()
            triggerEffect(RecentViewContract.Effect.OpenWorkspace)
        } catch (exception: Exception) {
            if (loadingText != null) {
                navigator.popBackStack()
            }

            if (printStackTrace) {
                exception.printStackTrace()
            }

            snackbarHostState.showSnackbar(
                message = errorMessage,
                withDismissAction = true,
            )
        }
    }
}

sealed interface RecentViewContract {
    sealed interface Event {
        data object OnClickOpenProject : Event
        data object OnClickNewProject : Event

        data class OpenProjectFromHistory(
            val project: RecentWorkspace
        ): Event
        
        data class OnClickEditProject(
            val project: RecentWorkspace
        ): Event
    }

    sealed interface Effect {
        data object OpenWorkspace : Effect
    }
}
