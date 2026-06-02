package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class WorkspaceEventBroadcaster(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private val jobs = mutableListOf<Job>()
    private var verificationJob: Job? = null

    fun start() {
        if (jobs.isNotEmpty()) return

        jobs += scope.launch {
            WorkspaceRepository.bpm
                .drop(1)
                .collect { bpm ->
                    if (WorkspaceRepository.isApplyingRemoteBpmUpdate) {
                        WorkspaceRepository.markRemoteBpmUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.BpmChanged(bpm))
                        triggerVerification()
                    }
                }
        }

        jobs += scope.launch {
            WorkspaceRepository.projectName
                .drop(1)
                .filterNotNull()
                .collect { name ->
                    if (WorkspaceRepository.isApplyingRemoteProjectNameUpdate) {
                        WorkspaceRepository.markRemoteProjectNameUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.ProjectNameChanged(name))
                        triggerVerification()
                    }
                }
        }

        jobs += scope.launch {
            WorkspaceRepository.macros
                .drop(1)
                .collect { macros ->
                    if (WorkspaceRepository.isApplyingRemoteMacrosUpdate) {
                        WorkspaceRepository.markRemoteMacrosUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.MacrosChanged(macros))
                        triggerVerification()
                    }
                }
        }

        jobs += scope.launch {
            WorkspaceRepository.gridType
                .drop(1)
                .collect { gridType ->
                    if (WorkspaceRepository.isApplyingRemoteGridTypeUpdate) {
                        WorkspaceRepository.markRemoteGridTypeUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.GridTypeChanged(gridTypeToNetworkKey(gridType)))
                        triggerVerification()
                    }
                }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        verificationJob?.cancel()
        verificationJob = null
    }

    fun onBpmChanged(bpm: Double) {
        scope.launch {
            provider.send(ConnectEvent.BpmChanged(bpm))
            triggerVerification()
        }
    }

    fun onMacrosChanged(macros: List<Macro>) {
        scope.launch {
            provider.send(ConnectEvent.MacrosChanged(macros))
            triggerVerification()
        }
    }

    fun triggerVerification() {
        if (provider.localUser.value?.role != dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectRole.HOST) return

        verificationJob?.cancel()
        verificationJob = scope.launch {
            kotlinx.coroutines.delay(1000)
            val hash = WorkspaceRepository.getVerificationHash()
            provider.send(ConnectEvent.StateVerification(hash))
        }
    }
}
