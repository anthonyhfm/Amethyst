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

    fun start() {
        if (jobs.isNotEmpty()) return

        jobs += scope.launch {
            WorkspaceRepository.bpm
                .drop(1)
                .collect { bpm ->
                    if (WorkspaceRepository.isApplyingRemoteUpdate) {
                        WorkspaceRepository.markRemoteUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.BpmChanged(bpm))
                    }
                }
        }

        jobs += scope.launch {
            WorkspaceRepository.projectName
                .drop(1)
                .filterNotNull()
                .collect { name ->
                    if (WorkspaceRepository.isApplyingRemoteUpdate) {
                        WorkspaceRepository.markRemoteUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.ProjectNameChanged(name))
                    }
                }
        }

        jobs += scope.launch {
            WorkspaceRepository.macros
                .drop(1)
                .collect { macros ->
                    if (WorkspaceRepository.isApplyingRemoteUpdate) {
                        WorkspaceRepository.markRemoteUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.MacrosChanged(macros))
                    }
                }
        }

        jobs += scope.launch {
            WorkspaceRepository.gridType
                .drop(1)
                .collect { gridType ->
                    if (WorkspaceRepository.isApplyingRemoteUpdate) {
                        WorkspaceRepository.markRemoteUpdateConsumed()
                    } else {
                        provider.send(ConnectEvent.GridTypeChanged(gridTypeToNetworkKey(gridType)))
                    }
                }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun onBpmChanged(bpm: Double) {
        scope.launch {
            provider.send(ConnectEvent.BpmChanged(bpm))
        }
    }

    fun onMacrosChanged(macros: List<Macro>) {
        scope.launch {
            provider.send(ConnectEvent.MacrosChanged(macros))
        }
    }
}
