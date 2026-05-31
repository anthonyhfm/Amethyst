package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.network.connect.pathOf
import dev.anthonyhfm.amethyst.core.network.connect.toChainAddress
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ChainSyncBroadcaster(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private data class PendingStateChange(
        val chainPath: dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath,
        val path: dev.anthonyhfm.amethyst.core.network.connect.DevicePath,
        val state: DeviceState
    )

    private val stateChanges = MutableSharedFlow<PendingStateChange>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var stateJob: Job? = null

    fun start() {
        if (stateJob != null) return
        stateJob = scope.launch {
            stateChanges
                .debounce(50)
                .collect { pending ->
                    provider.send(
                        ConnectEvent.DeviceStateChanged(
                            chainPath = pending.chainPath,
                            path = pending.path,
                            state = pending.state
                        )
                    )
                }
        }
    }

    fun stop() {
        stateJob?.cancel()
        stateJob = null
    }

    fun onDevicePlaced(chain: Chain, device: GenericChainDevice<*>, atIndex: Int) {
        val address = chain.toChainAddress() ?: return
        scope.launch {
            provider.send(
                ConnectEvent.ChainDevicePlaced(
                    chainPath = address.chainPath,
                    parentPath = address.parentPath,
                    deviceId = device.selectionUUID,
                    atIndex = atIndex,
                    initialState = DeviceRegistry.pack(device)
                )
            )
        }
    }

    fun onDeviceRemoved(chain: Chain, deviceId: String) {
        val address = chain.toChainAddress() ?: return
        scope.launch {
            provider.send(
                ConnectEvent.ChainDeviceRemoved(
                    chainPath = address.chainPath,
                    parentPath = address.parentPath,
                    deviceId = deviceId
                )
            )
        }
    }

    fun onDeviceMoved(
        chainBefore: Chain,
        chainAfter: Chain,
        device: GenericChainDevice<*>,
        fromIndex: Int,
        toIndex: Int
    ) {
        val fromAddress = chainBefore.toChainAddress() ?: return
        val toAddress = chainAfter.toChainAddress() ?: return

        scope.launch {
            if (fromAddress.chainPath == toAddress.chainPath) {
                provider.send(
                    ConnectEvent.ChainDeviceMoved(
                        chainPath = fromAddress.chainPath,
                        fromParentPath = fromAddress.parentPath,
                        toParentPath = toAddress.parentPath,
                        deviceId = device.selectionUUID,
                        fromIndex = fromIndex,
                        toIndex = toIndex
                    )
                )
            } else {
                provider.send(
                    ConnectEvent.ChainDeviceRemoved(
                        chainPath = fromAddress.chainPath,
                        parentPath = fromAddress.parentPath,
                        deviceId = device.selectionUUID
                    )
                )
                provider.send(
                    ConnectEvent.ChainDevicePlaced(
                        chainPath = toAddress.chainPath,
                        parentPath = toAddress.parentPath,
                        deviceId = device.selectionUUID,
                        atIndex = toIndex,
                        initialState = DeviceRegistry.pack(device)
                    )
                )
            }
        }
    }

    fun onDeviceStateChanged(device: GenericChainDevice<*>, state: DeviceState) {
        val lightsPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.lightsChain.pathOf(device)
        val samplingPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.samplingChain.pathOf(device)
        val chainPath = when {
            lightsPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.LIGHTS
            samplingPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.SAMPLING
            else -> return
        }
        val path = lightsPath ?: samplingPath ?: return

        stateChanges.tryEmit(PendingStateChange(chainPath, path, DeviceRegistry.pack(device)))
    }

    fun onGroupStateChanged(device: GenericChainDevice<*>, before: DeviceState, after: DeviceState) {
        val lightsPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.lightsChain.pathOf(device)
        val samplingPath = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.samplingChain.pathOf(device)
        val chainPath = when {
            lightsPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.LIGHTS
            samplingPath != null -> dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath.SAMPLING
            else -> return
        }
        val path = lightsPath ?: samplingPath ?: return

        scope.launch {
            groupEvents(chainPath, path, before, after).forEach { provider.send(it) }
        }
    }

    private fun groupEvents(
        chainPath: dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath,
        devicePath: dev.anthonyhfm.amethyst.core.network.connect.DevicePath,
        before: DeviceState,
        after: DeviceState
    ): List<ConnectEvent> {
        return when {
            before is GroupChainDeviceState && after is GroupChainDeviceState ->
                diffGroups(chainPath, devicePath, before.groups, after.groups)
            before is MultiGroupChainDeviceState && after is MultiGroupChainDeviceState ->
                diffGroups(chainPath, devicePath, before.groups, after.groups)
            else -> emptyList()
        }
    }

    private fun diffGroups(
        chainPath: dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath,
        devicePath: dev.anthonyhfm.amethyst.core.network.connect.DevicePath,
        before: List<dev.anthonyhfm.amethyst.devices.effects.group.data.Group>,
        after: List<dev.anthonyhfm.amethyst.devices.effects.group.data.Group>
    ): List<ConnectEvent> {
        val beforeIds = before.map { it.id }
        val afterIds = after.map { it.id }
        val events = mutableListOf<ConnectEvent>()

        after.forEachIndexed { index, group ->
            if (group.id !in beforeIds) {
                events += ConnectEvent.GroupCreated(chainPath, devicePath, index, group.name)
            }
        }

        before.forEachIndexed { index, group ->
            if (group.id !in afterIds) {
                events += ConnectEvent.GroupRemoved(chainPath, devicePath, index)
            }
        }

        after.forEachIndexed { index, group ->
            val beforeIndex = beforeIds.indexOf(group.id)
            if (beforeIndex != -1 && beforeIndex != index) {
                events += ConnectEvent.GroupReordered(chainPath, devicePath, beforeIndex, index)
            }
            val beforeGroup = before.getOrNull(beforeIndex)
            if (beforeGroup != null && beforeGroup.name != group.name) {
                events += ConnectEvent.GroupRenamed(chainPath, devicePath, index, group.name)
            }
        }

        return events
    }
}
