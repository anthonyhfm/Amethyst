package dev.anthonyhfm.amethyst.core.network

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectRole
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectSession
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectionState
import dev.anthonyhfm.amethyst.core.network.lan.LanConnectProvider
import dev.anthonyhfm.amethyst.core.network.lan.LanDiscoveryService
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncBroadcaster
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncBroadcaster
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncReceiver
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator
import dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncReceiver
import dev.anthonyhfm.amethyst.core.network.sync.WorkspaceEventBroadcaster
import dev.anthonyhfm.amethyst.core.network.sync.WorkspaceEventReceiver
import dev.anthonyhfm.amethyst.core.network.sync.WorkspaceSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/**
 * Central orchestrator for the LAN collaboration feature.
 */
object CollaborationManager {

    val provider: LanConnectProvider = LanConnectProvider()

    val connectionState: StateFlow<ConnectionState> = provider.connectionState
    val session: StateFlow<ConnectSession?> = provider.session
    val localUser: StateFlow<ConnectUser?> = provider.localUser

    val isActive: Boolean
        get() = connectionState.value is ConnectionState.Connected

    val isHosting: Boolean
        get() = localUser.value?.role == ConnectRole.HOST && isActive

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var workspaceBroadcaster: WorkspaceEventBroadcaster? = null
    private var workspaceReceiver: WorkspaceEventReceiver? = null
    private var deviceBroadcaster: DeviceSyncBroadcaster? = null
    private var deviceReceiver: DeviceSyncReceiver? = null
    private var chainBroadcaster: ChainSyncBroadcaster? = null
    private var chainReceiver: ChainSyncReceiver? = null

    suspend fun startHosting(
        sessionName: String,
        localUser: ConnectUser
    ): Result<ConnectSession> {
        val result = provider.host(sessionName, localUser)
        if (result.isSuccess) {
            val session = result.getOrThrow()
            LanDiscoveryService.startBroadcasting(session)
            startSync(hosting = true)
        }
        return result
    }

    suspend fun joinSession(
        hostAddress: String,
        localUser: ConnectUser
    ): Result<ConnectSession> {
        val result = provider.join(hostAddress, localUser)
        if (result.isSuccess) {
            startSync(hosting = false)
        }
        return result
    }

    suspend fun leaveSession() {
        stopSync()
        LanDiscoveryService.stopBroadcasting()
        provider.leave()
    }

    suspend fun sendIfActive(event: ConnectEvent) {
        if (isActive) provider.send(event)
    }

    private fun startSync(hosting: Boolean) {
        stopSync()

        workspaceReceiver = WorkspaceEventReceiver(provider, scope).also { it.start() }
        deviceReceiver = DeviceSyncReceiver(provider, scope).also { it.start() }
        chainReceiver = ChainSyncReceiver(provider, scope).also { it.start() }
        CollaborationPresence.attach(provider, scope)

        workspaceBroadcaster = WorkspaceEventBroadcaster(provider, scope).also {
            it.start()
            WorkspaceSyncCoordinator.attach(it)
        }

        deviceBroadcaster = DeviceSyncBroadcaster(provider, scope).also {
            DeviceSyncCoordinator.attach(it)
        }
        chainBroadcaster = ChainSyncBroadcaster(provider, scope).also {
            it.start()
            ChainSyncCoordinator.attach(it)
        }
    }

    private fun stopSync() {
        workspaceBroadcaster?.stop()
        workspaceBroadcaster?.let { WorkspaceSyncCoordinator.detach(it) }
        workspaceBroadcaster = null

        workspaceReceiver?.stop()
        workspaceReceiver = null

        deviceReceiver?.stop()
        deviceReceiver = null

        deviceBroadcaster?.let { DeviceSyncCoordinator.detach(it) }
        deviceBroadcaster = null

        chainReceiver?.stop()
        chainReceiver = null

        chainBroadcaster?.let {
            it.stop()
            ChainSyncCoordinator.detach(it)
        }
        chainBroadcaster = null

        CollaborationPresence.detach()
    }
}
