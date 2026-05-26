package dev.anthonyhfm.amethyst.desktop

import dev.anthonyhfm.amethyst.settings.data.DiscordSettings
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vyfor.kpresence.RichClient
import io.github.vyfor.kpresence.rpc.ActivityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

actual object DiscordRPCManager {
    private val appId: Long = 1402215916573298869
    private var client: RichClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _currentProjectName = MutableStateFlow<String?>(null)
    val currentProjectName: StateFlow<String?> = _currentProjectName.asStateFlow()

    actual fun initialize() {
        scope.launch {
            WorkspaceRepository.projectName.collect { projectName ->
                _currentProjectName.value = projectName
            }
        }

        scope.launch {
            combine(
                _isConnected,
                _currentProjectName,
                WorkspaceRepository.mode
            ) { isConnected, projectName, mode ->
                Triple(isConnected, projectName, mode)
            }.collect { (isConnected, projectName, mode) ->
                if (isConnected) {
                    updatePresence(
                        projectName = projectName,
                        workspaceState = mode,
                        showProject = DiscordSettings.showCurrentProject.value,
                        showState = DiscordSettings.showCurrentWorkspaceState.value
                    )
                }
            }
        }
        
        if (DiscordSettings.enableDiscordRPC.value) {
            connect()
        }
    }

    fun connect() {
        if (_isConnected.value) return

        scope.launch(Dispatchers.IO) {
            try {
                client = RichClient(appId)
                client?.connect()
                _isConnected.value = true

                updatePresence(
                    projectName = _currentProjectName.value,
                    workspaceState = WorkspaceRepository.mode.value,
                    showProject = DiscordSettings.showCurrentProject.value,
                    showState = DiscordSettings.showCurrentWorkspaceState.value
                )
            } catch (e: Exception) {
                _isConnected.value = false
            }
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            try {
                client?.shutdown()
            } catch (e: Exception) {
                // ignore — still clean up state below
            } finally {
                client = null
                _isConnected.value = false
            }
        }
    }

    actual fun setProjectName(name: String?) {
        _currentProjectName.value = name
    }

    private fun updatePresence(
        projectName: String?,
        workspaceState: WorkspaceContract.WorkspaceMode,
        showProject: Boolean,
        showState: Boolean
    ) {
        if (!_isConnected.value || client == null) return
        
        try {
            client?.update {
                type = ActivityType.GAME
                
                details = if (showProject && projectName != null) {
                    projectName
                } else {
                    "Home"
                }
                
                if (showState && projectName != null) {
                    state = formatWorkspaceMode(workspaceState)
                } else {
                    state = null
                }

                assets {
                    largeImage = "amethyst_studio_logo"
                    largeText = "Amethyst"
                }
            }
        } catch (e: Exception) { }
    }
    
    /**
     * Format workspace mode for display.
     */
    private fun formatWorkspaceMode(mode: WorkspaceContract.WorkspaceMode): String {
        return when (mode) {
            is WorkspaceContract.WorkspaceMode.Layout -> "Layout Mode"
            is WorkspaceContract.WorkspaceMode.Timeline -> "In the Timeline"
            is WorkspaceContract.WorkspaceMode.Performance -> "Preview Mode"
            is WorkspaceContract.WorkspaceMode.LightsChain -> "Lights Chain Mode"
            is WorkspaceContract.WorkspaceMode.SamplingChain -> "Sampling Chain Mode"
            is KeyframesWorkspaceMode -> "Editing Keyframes"
            is CoordinateFilterWorkspaceMode -> "Editing Coordinate Filters"

            else -> "Working on Project"
        }
    }

    actual fun toggleRPC(enabled: Boolean) {
        if (enabled) {
            connect()
        } else {
            disconnect()
        }
    }

    actual fun forceUpdate() {
        if (_isConnected.value) {
            updatePresence(
                projectName = _currentProjectName.value,
                workspaceState = WorkspaceRepository.mode.value,
                showProject = DiscordSettings.showCurrentProject.value,
                showState = DiscordSettings.showCurrentWorkspaceState.value
            )
        }
    }
}
