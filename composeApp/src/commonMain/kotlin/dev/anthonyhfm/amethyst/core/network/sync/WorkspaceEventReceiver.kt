package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectRole
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WorkspaceEventReceiver(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope,
    private val onFullStateSyncApplied: () -> Unit = {}
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return

        job = scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is ConnectEvent.StateVerification ->
                        handleStateVerification(event)

                    is ConnectEvent.BpmChanged -> {
                        WorkspaceRepository.setBpm(event.bpm, fromRemote = true)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.ProjectNameChanged -> {
                        WorkspaceRepository.setProjectName(event.name, fromRemote = true)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.MacrosChanged -> {
                        WorkspaceRepository.setMacros(event.macros, fromRemote = true, undoable = false)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.GridTypeChanged -> {
                        WorkspaceRepository.setGridType(
                            gridTypeFromNetworkKey(event.gridTypeKey),
                            fromRemote = true
                        )
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.FullStateSync -> {
                        handleWorkspaceSnapshot(
                            workspaceData = event.workspaceData,
                            bpm = event.bpm,
                            projectName = event.projectName,
                            macros = event.macros,
                            source = "FullStateSync"
                        )
                        onFullStateSyncApplied()
                    }

                    is ConnectEvent.RequestResync ->
                        handleRequestResync(event)

                    is ConnectEvent.ResyncResponse ->
                        handleWorkspaceSnapshot(
                            workspaceData = event.workspaceData,
                            bpm = event.bpm,
                            projectName = event.projectName,
                            macros = event.macros,
                            source = "ResyncResponse"
                        )

                    else -> { /* handled by other receivers */ }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun handleStateVerification(event: ConnectEvent.StateVerification) {
        val role = provider.localUser.value?.role
        if (role == ConnectRole.HOST) return

        val localHash = WorkspaceRepository.getVerificationHash()
        if (localHash != event.expectedHash) {
            println("Workspace state mismatch detected! Expected: ${event.expectedHash}, got: $localHash. Requesting resync.")
            requestResync()
        }
    }

    private fun requestResync() {
        val userId = provider.localUser.value?.id ?: return
        scope.launch {
            provider.send(ConnectEvent.RequestResync(userId))
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun handleWorkspaceSnapshot(
        workspaceData: ByteArray,
        bpm: Double,
        projectName: String,
        macros: List<Macro>,
        source: String
    ) {
        try {
            val data = AmethystProtoBuf.decodeFromByteArray(
                SavableWorkspaceData.serializer(),
                workspaceData
            )
            WorkspaceRepository.loadWorkspace(data, fromRemote = true)
            WorkspaceRepository.setBpm(bpm, fromRemote = true, undoable = false)
            WorkspaceRepository.setProjectName(projectName, fromRemote = true)
            WorkspaceRepository.setMacros(macros, fromRemote = true, undoable = false)
            WorkspaceRepository.deviceRefresh.emit(Unit)
        } catch (e: Exception) {
            println("WorkspaceEventReceiver: Failed to apply $source — ${e.message}")
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun handleRequestResync(event: ConnectEvent.RequestResync) {
        if (provider.localUser.value?.role != ConnectRole.HOST) return

        val data = WorkspaceRepository.saveWorkspace()
        val bytes = AmethystProtoBuf.encodeToByteArray(SavableWorkspaceData.serializer(), data)
        provider.sendToUser(
            userId = event.userId,
            event = ConnectEvent.ResyncResponse(
                workspaceData = bytes,
                bpm = WorkspaceRepository.bpm.value,
                projectName = WorkspaceRepository.projectName.value ?: "",
                macros = WorkspaceRepository.macros.value
            )
        )
    }
}
