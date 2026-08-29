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
    private val onFullStateSyncApplied: () -> Unit = {},
    private val onFullStateSyncProgress: (phase: String, progress: Float?, detail: String) -> Unit = { _, _, _ -> }
) {
    private var job: Job? = null
    private val pendingFullStateSyncChunks = mutableMapOf<String, PendingFullStateSync>()

    private data class PendingFullStateSync(
        val chunkCount: Int,
        val chunks: Array<ByteArray?>,
        val bpm: Double,
        val projectName: String,
        val macros: List<Macro>
    )

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    private fun log(message: String) {
        println("[WorkspaceReceiver ${nowMillis()}] $message")
    }

    private fun bytesLabel(bytes: Int): String =
        "${bytes}B (${bytes / 1024.0 / 1024.0}MiB)"

    fun start() {
        if (job != null) return
        log("start()")

        job = scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is ConnectEvent.StateVerification -> {
                        log("event StateVerification expectedHash=${event.expectedHash}")
                        handleStateVerification(event)
                    }

                    is ConnectEvent.BpmChanged -> {
                        log("event BpmChanged bpm=${event.bpm}")
                        WorkspaceRepository.setBpm(event.bpm, fromRemote = true)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.ProjectNameChanged -> {
                        log("event ProjectNameChanged name='${event.name}'")
                        WorkspaceRepository.setProjectName(event.name, fromRemote = true)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.MacrosChanged -> {
                        log("event MacrosChanged count=${event.macros.size}")
                        WorkspaceRepository.syncMacrosSize(event.macros, fromRemote = true)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.ParameterMappingsChanged -> {
                        log("event ParameterMappingsChanged count=${event.mappings.size}")
                        WorkspaceRepository.setParameterMappings(
                            event.mappings,
                            fromRemote = true,
                            undoable = false,
                        )
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.GridTypeChanged -> {
                        log("event GridTypeChanged key=${event.gridTypeKey}")
                        WorkspaceRepository.setGridType(
                            gridTypeFromNetworkKey(event.gridTypeKey),
                            fromRemote = true
                        )
                        WorkspaceSyncCoordinator.triggerVerification()
                    }

                    is ConnectEvent.FullStateSync -> {
                        log("event FullStateSync bytes=${bytesLabel(event.workspaceData.size)} bpm=${event.bpm} project='${event.projectName}' macros=${event.macros.size}")
                        onFullStateSyncProgress("Receiving workspace", 0.9f, bytesLabel(event.workspaceData.size))
                        val applied = handleWorkspaceSnapshot(
                            workspaceData = event.workspaceData,
                            bpm = event.bpm,
                            projectName = event.projectName,
                            macros = event.macros,
                            source = "FullStateSync"
                        )
                        if (applied) onFullStateSyncApplied()
                    }

                    is ConnectEvent.FullStateSyncChunk -> {
                        handleFullStateSyncChunk(event)
                    }

                    is ConnectEvent.RequestResync -> {
                        log("event RequestResync userId=${event.userId}")
                        handleRequestResync(event)
                    }

                    is ConnectEvent.ResyncResponse -> {
                        log("event ResyncResponse bytes=${bytesLabel(event.workspaceData.size)} bpm=${event.bpm} project='${event.projectName}' macros=${event.macros.size}")
                        handleWorkspaceSnapshot(
                            workspaceData = event.workspaceData,
                            bpm = event.bpm,
                            projectName = event.projectName,
                            macros = event.macros,
                            source = "ResyncResponse"
                        )
                    }

                    else -> { /* handled by other receivers */ }
                }
            }
        }
    }

    private suspend fun handleFullStateSyncChunk(event: ConnectEvent.FullStateSyncChunk) {
        if (event.chunkIndex !in 0 until event.chunkCount) {
            log("event FullStateSyncChunk invalid index=${event.chunkIndex} count=${event.chunkCount} transfer=${event.transferId}")
            return
        }

        val pending = pendingFullStateSyncChunks.getOrPut(event.transferId) {
            log("event FullStateSyncChunk transfer=${event.transferId} start count=${event.chunkCount} bpm=${event.bpm} project='${event.projectName}' macros=${event.macros.size}")
            onFullStateSyncProgress("Receiving workspace", 0f, "0/${event.chunkCount} chunks")
            PendingFullStateSync(
                chunkCount = event.chunkCount,
                chunks = arrayOfNulls(event.chunkCount),
                bpm = event.bpm,
                projectName = event.projectName,
                macros = event.macros
            )
        }

        if (pending.chunkCount != event.chunkCount) {
            log("event FullStateSyncChunk count mismatch transfer=${event.transferId} expected=${pending.chunkCount} got=${event.chunkCount}")
            pendingFullStateSyncChunks.remove(event.transferId)
            return
        }

        pending.chunks[event.chunkIndex] = event.workspaceDataChunk
        val received = pending.chunks.count { it != null }
        log("event FullStateSyncChunk transfer=${event.transferId} chunk=${event.chunkIndex + 1}/${event.chunkCount} bytes=${bytesLabel(event.workspaceDataChunk.size)} received=$received/${event.chunkCount}")
        onFullStateSyncProgress(
            "Receiving workspace",
            received.toFloat() / event.chunkCount.toFloat(),
            "$received/${event.chunkCount} chunks"
        )

        if (received != event.chunkCount) return

        onFullStateSyncProgress("Reassembling workspace", 0.92f, bytesLabel(pending.chunks.sumOf { it?.size ?: 0 }))
        val totalSize = pending.chunks.sumOf { it?.size ?: 0 }
        val workspaceData = ByteArray(totalSize)
        var offset = 0
        pending.chunks.forEachIndexed { index, chunk ->
            if (chunk == null) {
                log("event FullStateSyncChunk missing chunk index=$index transfer=${event.transferId}")
                pendingFullStateSyncChunks.remove(event.transferId)
                return
            }
            chunk.copyInto(workspaceData, destinationOffset = offset)
            offset += chunk.size
        }
        pendingFullStateSyncChunks.remove(event.transferId)

        log("event FullStateSyncChunk transfer=${event.transferId} reassembled bytes=${bytesLabel(workspaceData.size)}")
        val applied = handleWorkspaceSnapshot(
            workspaceData = workspaceData,
            bpm = pending.bpm,
            projectName = pending.projectName,
            macros = pending.macros,
            source = "FullStateSyncChunk"
        )
        if (applied) onFullStateSyncApplied()
    }

    fun stop() {
        log("stop()")
        job?.cancel()
        job = null
        pendingFullStateSyncChunks.clear()
    }

    private fun handleStateVerification(event: ConnectEvent.StateVerification) {
        val role = provider.localUser.value?.role
        if (role == ConnectRole.HOST) return

        val localHash = WorkspaceRepository.getVerificationHash()
        if (localHash != event.expectedHash) {
            log("state mismatch expected=${event.expectedHash} got=$localHash; requesting resync")
            requestResync()
        } else {
            log("state verification ok hash=$localHash")
        }
    }

    private fun requestResync() {
        val userId = provider.localUser.value?.id ?: return
        log("requestResync() userId=$userId")
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
    ): Boolean {
        try {
            val start = nowMillis()
            onFullStateSyncProgress("Decoding workspace", 0.94f, bytesLabel(workspaceData.size))
            log("$source decode start bytes=${bytesLabel(workspaceData.size)}")
            val data = AmethystProtoBuf.decodeFromByteArray(
                SavableWorkspaceData.serializer(),
                workspaceData
            )
            val decodedAt = nowMillis()
            log("$source decode done in ${decodedAt - start}ms tracks=${data.timelineData.size} audioSources=${data.audioSources.size} launchpads=${data.launchpadDevices.size} macros=${data.macros.size}")
            log("$source loadWorkspace start")
            onFullStateSyncProgress("Loading workspace", 0.97f, "${data.timelineData.size} tracks, ${data.audioSources.size} audio sources")
            WorkspaceRepository.loadWorkspace(data, fromRemote = true)
            val loadedAt = nowMillis()
            log("$source loadWorkspace done in ${loadedAt - decodedAt}ms")
            WorkspaceRepository.setBpm(bpm, fromRemote = true, undoable = false)
            WorkspaceRepository.setProjectName(projectName, fromRemote = true)
            WorkspaceRepository.syncMacrosSize(macros, fromRemote = true)
            WorkspaceRepository.deviceRefresh.emit(Unit)
            log("$source apply complete total=${nowMillis() - start}ms")
            onFullStateSyncProgress("Workspace loaded", 1f, "Ready")
            return true
        } catch (e: Exception) {
            log("failed to apply $source ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            onFullStateSyncProgress("Failed to load workspace", null, e.message ?: e::class.simpleName.orEmpty())
            return false
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun handleRequestResync(event: ConnectEvent.RequestResync) {
        if (provider.localUser.value?.role != ConnectRole.HOST) return

        log("handleRequestResync() userId=${event.userId}")
        val data = WorkspaceRepository.saveWorkspace()
        val bytes = AmethystProtoBuf.encodeToByteArray(
            SavableWorkspaceData.serializer(),
            data.withLocalMacroValuesStripped()
        )
        log("handleRequestResync() encoded bytes=${bytesLabel(bytes.size)}")
        provider.sendToUser(
            userId = event.userId,
            event = ConnectEvent.ResyncResponse(
                workspaceData = bytes,
                bpm = WorkspaceRepository.bpm.value,
                projectName = WorkspaceRepository.projectName.value ?: "",
                macros = WorkspaceRepository.macros.value.asMacroStructure()
            )
        )
    }
}

private fun SavableWorkspaceData.withLocalMacroValuesStripped(): SavableWorkspaceData =
    copy(macros = macros.asMacroStructure())

private fun List<Macro>.asMacroStructure(): List<Macro> = map(Macro::withoutLocalValue)
