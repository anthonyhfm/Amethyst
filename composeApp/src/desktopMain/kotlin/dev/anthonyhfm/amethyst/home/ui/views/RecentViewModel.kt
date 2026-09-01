package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.core.network.CollaborationManager
import dev.anthonyhfm.amethyst.core.network.lan.DiscoveredSession
import dev.anthonyhfm.amethyst.core.network.lan.LanDiscoveryService
import dev.anthonyhfm.amethyst.core.network.user.LocalUserRepository
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineProjectArchiveFormat
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.home.nav.HomeNavRoute
import dev.anthonyhfm.amethyst.settings.data.ExperimentalSettings
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecentViewModel(
    private val navigator: NavHostController,
    private val snackbarHostState: SnackbarHostState
) : BaseViewModel<RecentViewContract.State, RecentViewContract.Event, RecentViewContract.Effect>(
    RecentViewContract.State()
) {
    private fun log(message: String) {
        println("[RecentViewModel ${System.currentTimeMillis()}] $message")
    }

    init {
        viewModelScope.launch {
            ExperimentalSettings.liveCollaboration.flow.collectLatest { enabled ->
                if (!enabled) {
                    updateState(
                        state.value.copy(
                            discoveredSessions = emptyList(),
                            isDiscovering = false
                        )
                    )
                    return@collectLatest
                }

                updateState(
                    state.value.copy(
                        discoveredSessions = emptyList(),
                        isDiscovering = true
                    )
                )
                LanDiscoveryService.discoverSessions().collect { sessions ->
                    updateState(
                        state.value.copy(
                            discoveredSessions = sessions,
                            isDiscovering = true
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            CollaborationManager.initialSyncProgress.collect { progress ->
                updateState(state.value.copy(initialSyncProgress = progress))
            }
        }
    }

    override fun onEvent(event: RecentViewContract.Event) {
        when (event) {
            is RecentViewContract.Event.OnClickOpenProject -> {
                viewModelScope.launch {
                    val file = FileKit.openFilePicker(
                        type = FileKitType.File(
                            extensions = listOf("ame", "als", "zip", "rar", "approj")
                        ),
                        title = getString(Res.string.home_recent_dialog_file_picker_title)
                    )

                    if (file == null) return@launch

                    when (file.extension.lowercase()) {
                        "ame" -> { // Native Amethyst Projects
                            runWorkspaceLoad(
                                loadingText = getString(Res.string.home_recent_loading_project_msg),
                                errorMessage = getString(Res.string.home_recent_invalid_project_msg),
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
                                loadingText = getString(Res.string.home_recent_translating_apollo_msg),
                                errorMessage = getString(Res.string.home_recent_failed_apollo_msg),
                                printStackTrace = true,
                            ) {
                                val workspace = HomeRepository.loadWorkspaceData(file)
                                HomeRepository.openWorkspace(workspace)
                            }
                        }

                        "zip", "rar" -> {
                            val format = determineProjectArchiveFormat(file)

                            when (format) {
                                ZippedProjectFormat.ABLETON -> {
                                    navigator.navigate(HomeNavRoute.AbletonImportWizard(file.path))
                                }

                                ZippedProjectFormat.ABLETON_APOLLO -> {
                                    runWorkspaceLoad(
                                        loadingText = getString(Res.string.home_recent_translating_ableton_apollo_msg),
                                        errorMessage = getString(Res.string.home_recent_failed_ableton_apollo_msg),
                                        printStackTrace = true,
                                    ) {
                                        val workspace = HomeRepository.loadWorkspaceData(file)
                                        HomeRepository.openWorkspace(workspace)
                                    }
                                }

                                ZippedProjectFormat.UNIPAD -> {
                                    runWorkspaceLoad(
                                        loadingText = getString(Res.string.home_recent_translating_unipad_msg),
                                        errorMessage = getString(Res.string.home_recent_failed_unipad_msg),
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
                        errorMessage = getString(Res.string.home_recent_failed_open_recent_msg),
                        printStackTrace = true,
                    ) {
                        HomeRepository.openRecentWorkspace(event.project)
                    }
                }
            }
            
            is RecentViewContract.Event.OnClickEditProject -> {
                navigator.navigate(HomeNavRoute.ProjectEdit(projectPath = event.project.path))
            }

            is RecentViewContract.Event.OnClickJoinSession -> {
                log("OnClickJoinSession session=${event.session.session.id}/${event.session.session.name} hostAddress=${event.session.hostAddress} userName='${event.userName}'")
                viewModelScope.launch {
                    if (!ExperimentalSettings.liveCollaboration.value) {
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.home_recent_collab_disabled_msg),
                            withDismissAction = true,
                        )
                        return@launch
                    }

                    try {
                        LocalUserRepository.setUsername(event.userName)
                        log("joinSession() call localUser=${LocalUserRepository.localUser.value.id}/${LocalUserRepository.localUser.value.name}")
                        val result = CollaborationManager.joinSession(
                            hostAddress = event.session.hostAddress,
                            localUser = LocalUserRepository.localUser.value
                        )
                        log("joinSession() returned success=${result.isSuccess} exception=${result.exceptionOrNull()?.message}")
                        result.getOrThrow()
                        log("triggerEffect(OpenWorkspace)")
                        triggerEffect(RecentViewContract.Effect.OpenWorkspace)
                    } catch (exception: Exception) {
                        log("join failed ${exception::class.simpleName}: ${exception.message}")
                        exception.printStackTrace()
                        snackbarHostState.showSnackbar(
                            message = getString(Res.string.home_recent_collab_failed_join_msg),
                            withDismissAction = true,
                        )
                    }
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
        val initialText = loadingText ?: "Starte Ladevorgang..."
        dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.startLoading(
            initialTitle = "PROJEKT WIRD GELADEN",
            initialStatus = initialText
        )
        navigator.navigate(HomeNavRoute.LoadingScreen(initialText))

        try {
            block()
            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.finishLoading()
            triggerEffect(RecentViewContract.Effect.OpenWorkspace)
        } catch (exception: Exception) {
            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.finishLoading()
            navigator.popBackStack()

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
    data class State(
        val discoveredSessions: List<DiscoveredSession> = emptyList(),
        val isDiscovering: Boolean = false,
        val initialSyncProgress: CollaborationManager.InitialSyncProgress = CollaborationManager.InitialSyncProgress()
    )

    sealed interface Event {
        data object OnClickOpenProject : Event
        data object OnClickNewProject : Event

        data class OpenProjectFromHistory(
            val project: RecentWorkspace
        ): Event
        
        data class OnClickEditProject(
            val project: RecentWorkspace
        ): Event

        data class OnClickJoinSession(
            val session: DiscoveredSession,
            val userName: String
        ) : Event
    }

    sealed interface Effect {
        data object OpenWorkspace : Effect
    }
}
