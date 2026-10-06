package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.core.util.MobileFileStorage
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineProjectArchiveFormat
import dev.anthonyhfm.amethyst.core.util.fileDialogSettings
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.home.data.AndroidLocalProjectDeletion
import dev.anthonyhfm.amethyst.home.data.AndroidProjectImporter
import dev.anthonyhfm.amethyst.home.data.MobileProjectRecord
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectsViewModel(
    private val navigator: NavHostController,
    private val snackbarHostState: SnackbarHostState,
) : BaseViewModel<Nothing?, ProjectsViewContract.Event, ProjectsViewContract.Effect>(null) {

    override fun onEvent(event: ProjectsViewContract.Event) {
        when (event) {
            is ProjectsViewContract.Event.OnClickOpenProject -> {
                viewModelScope.launch {
                    val file = FileKit.openFilePicker(
                        // Android's MimeTypeMap does not know Amethyst's custom
                        // ".ame" extension. Mixing it with ".zip" therefore
                        // makes the system picker filter for ZIP files only and
                        // greys out valid Amethyst projects.
                        type = FileKitType.File(),
                        dialogSettings = fileDialogSettings(
                            title = getString(Res.string.home_projects_dialog_file_picker_title),
                        ),
                    )

                    if (file == null) return@launch

                    val extension = file.extension.lowercase()
                    if (extension !in SUPPORTED_PROJECT_EXTENSIONS) {
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.home_projects_invalid_project_msg),
                            withDismissAction = true,
                        )
                        return@launch
                    }

                    val imported = try {
                        AndroidProjectImporter.importOriginal(file)
                    } catch (error: Exception) {
                        error.printStackTrace()
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.home_projects_file_read_failed),
                            withDismissAction = true,
                        )
                        return@launch
                    }
                    val persistentFile = imported.file
                    HomeRepository.registerMobileProject(
                        MobileProjectRecord(
                            id = imported.id,
                            title = file.name.substringBeforeLast('.', file.name),
                            originalPath = persistentFile.path,
                            importedAt = System.currentTimeMillis(),
                            sourceHash = imported.sha256,
                        )
                    )

                    when (extension) {
                        "ame" -> {
                            runWorkspaceLoad(
                                loadingText = getString(Res.string.home_projects_loading_project_msg),
                                errorMessage = getString(Res.string.home_projects_invalid_project_msg),
                            ) {
                                val workspace = HomeRepository.loadWorkspaceData(persistentFile)
                                HomeRepository.openWorkspace(workspace, rememberRecent = true)
                            }
                        }

                        "als" -> {
                            navigator.navigate(HomeNavRoute.AbletonImportWizard(persistentFile.absolutePath()))
                        }

                        "approj" -> {
                            runWorkspaceLoad(
                                loadingText = getString(Res.string.home_projects_translating_apollo_msg),
                                errorMessage = getString(Res.string.home_projects_failed_apollo_msg),
                                printStackTrace = true,
                            ) {
                                val workspace = HomeRepository.loadWorkspaceData(persistentFile)
                                HomeRepository.openWorkspace(workspace, rememberRecent = true)
                            }
                        }

                        "zip", "rar" -> {
                            val format = try {
                                withContext(context = Dispatchers.IO) {
                                    determineProjectArchiveFormat(file = persistentFile)
                                }
                            } catch (error: Exception) {
                                error.printStackTrace()
                                snackbarHostState.showSnackbar(
                                    message = getString(Res.string.home_projects_invalid_project_msg),
                                    withDismissAction = true,
                                )
                                return@launch
                            }

                            when (format) {
                                ZippedProjectFormat.ABLETON -> {
                                    navigator.navigate(HomeNavRoute.AbletonImportWizard(persistentFile.path))
                                }

                                ZippedProjectFormat.ABLETON_APOLLO -> {
                                    runWorkspaceLoad(
                                        loadingText = getString(Res.string.home_projects_translating_ableton_apollo_msg),
                                        errorMessage = getString(Res.string.home_projects_failed_ableton_apollo_msg),
                                        printStackTrace = true,
                                    ) {
                                        val workspace = HomeRepository.loadWorkspaceData(persistentFile)
                                        HomeRepository.openWorkspace(workspace, rememberRecent = true)
                                    }
                                }

                                ZippedProjectFormat.UNIPAD -> {
                                    runWorkspaceLoad(
                                        loadingText = getString(Res.string.home_projects_translating_unipad_msg),
                                        errorMessage = getString(Res.string.home_projects_failed_unipad_msg),
                                    ) {
                                        val workspace = HomeRepository.loadWorkspaceData(persistentFile)
                                        HomeRepository.openWorkspace(workspace, rememberRecent = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is ProjectsViewContract.Event.OnClickNewProject -> {
                navigator.navigate(route = HomeNavRoute.ProjectCreation)
            }

            is ProjectsViewContract.Event.OpenProjectFromHistory -> {
                viewModelScope.launch {
                    val recentFile = MobileFileStorage.resolvePath(event.project.path)
                    if (!HomeRepository.hasConvertedMobileProject(recentFile.path) &&
                        withContext(context = Dispatchers.IO) {
                            recentFile.extension.equals("als", ignoreCase = true) ||
                                (recentFile.extension.lowercase() in setOf("zip", "rar") &&
                                    runCatching {
                                        determineProjectArchiveFormat(file = recentFile)
                                    }.getOrNull() == ZippedProjectFormat.ABLETON)
                        }
                    ) {
                        navigator.navigate(HomeNavRoute.AbletonImportWizard(recentFile.path))
                        return@launch
                    }
                    runWorkspaceLoad(
                        loadingText = getString(Res.string.home_projects_opening_project_msg),
                        errorMessage = getString(Res.string.home_projects_failed_open_recent_msg),
                        printStackTrace = true,
                    ) {
                        HomeRepository.openRecentWorkspace(event.project)
                    }
                }
            }

            is ProjectsViewContract.Event.OnClickEditProject -> {
                navigator.navigate(
                    route = HomeNavRoute.ProjectEdit(projectPath = event.project.path)
                )
            }

            is ProjectsViewContract.Event.OnClickDeleteProject -> {
                viewModelScope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        AndroidLocalProjectDeletion.delete(event.path)
                    }
                    if (deleted) {
                        HomeRepository.removeRecentWorkspace(event.path)
                        triggerEffect(ProjectsViewContract.Effect.ProjectDeleted)
                    }
                    else snackbarHostState.showSnackbar(
                        message = getString(Res.string.home_projects_delete_local_error),
                        withDismissAction = true,
                    )
                }
            }
        }
    }

    private suspend fun runWorkspaceLoad(
        loadingText: String? = null,
        errorMessage: String,
        printStackTrace: Boolean = false,
        block: suspend () -> Unit,
    ) {
        val initialText = loadingText ?: getString(Res.string.home_loading_default_status)
        dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.startLoading(
            initialTitle = getString(Res.string.home_loading_default_title),
            initialStatus = initialText,
        )
        if (loadingText != null) {
            navigator.navigate(HomeNavRoute.LoadingScreen(loadingText))
        }

        try {
            block()
            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.finishLoading()
            triggerEffect(ProjectsViewContract.Effect.OpenWorkspace)
        } catch (exception: Exception) {
            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.finishLoading()
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

    private companion object {
        val SUPPORTED_PROJECT_EXTENSIONS = setOf("ame", "als", "zip", "rar", "approj")
    }
}

sealed interface ProjectsViewContract {
    sealed interface Event {
        data object OnClickOpenProject : Event
        data object OnClickNewProject : Event

        data class OpenProjectFromHistory(
            val project: RecentWorkspace,
        ) : Event

        data class OnClickEditProject(
            val project: RecentWorkspace,
        ) : Event

        data class OnClickDeleteProject(
            val path: String,
        ) : Event
    }

    sealed interface Effect {
        data object OpenWorkspace : Effect
        data object ProjectDeleted : Effect
    }
}
