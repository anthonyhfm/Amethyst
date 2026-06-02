package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.network.connect.resolve
import dev.anthonyhfm.amethyst.core.network.connect.resolveChain
import dev.anthonyhfm.amethyst.core.network.connect.resolveRootChain
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChainSyncReceiver(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return

        job = scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is ConnectEvent.ChainDevicePlaced -> {
                        handleDevicePlaced(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.ChainDeviceRemoved -> {
                        handleDeviceRemoved(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.ChainDeviceMoved -> {
                        handleDeviceMoved(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.DeviceStateChanged -> {
                        handleStateChanged(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.GroupCreated -> {
                        handleGroupCreated(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.GroupRemoved -> {
                        handleGroupRemoved(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.GroupReordered -> {
                        handleGroupReordered(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.GroupRenamed -> {
                        handleGroupRenamed(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun handleDevicePlaced(event: ConnectEvent.ChainDevicePlaced) {
        val chain = event.chainPath.resolveChain(event.parentPath) ?: return
        if (chain.devices.value.any { it.selectionUUID == event.deviceId }) return

        val device = DeviceRegistry.createFromState(event.initialState)
        device.selectionUUID = event.deviceId
        chain.add(device, atIndex = event.atIndex, fromUser = false)
    }

    private fun handleDeviceRemoved(event: ConnectEvent.ChainDeviceRemoved) {
        event.chainPath.resolveChain(event.parentPath)?.remove(event.deviceId, fromUser = false)
    }

    private fun handleDeviceMoved(event: ConnectEvent.ChainDeviceMoved) {
        val fromChain = event.chainPath.resolveChain(event.fromParentPath) ?: return
        val toChain = event.chainPath.resolveChain(event.toParentPath) ?: return
        val sourceIndex = fromChain.devices.value.indexOfFirst { it.selectionUUID == event.deviceId }
            .takeIf { it >= 0 }
            ?: event.fromIndex.takeIf { it in fromChain.devices.value.indices }
            ?: return
        val device = fromChain.devices.value[sourceIndex]

        fromChain.remove(device.selectionUUID, fromUser = false)
        val targetIndex = event.toIndex.coerceIn(0, toChain.devices.value.size)
        toChain.add(device, atIndex = targetIndex, fromUser = false)
    }

    private fun handleStateChanged(event: ConnectEvent.DeviceStateChanged) {
        val device = event.chainPath.resolveRootChain().resolve(event.path) ?: run {
            requestResync()
            return
        }
        if (restoreNestedState(device, event.state)) return
        setDeviceState(device, event.state)
        device.onStateRestored()
    }

    private fun handleGroupCreated(event: ConnectEvent.GroupCreated) {
        val device = event.chainPath.resolveRootChain().resolve(event.devicePath) ?: return
        val group = Group(name = event.groupName)
        when (device) {
            is GroupChainDevice -> device.addGroupInternal(event.groupIndex, group)
            is MultiGroupChainDevice -> device.addGroupInternal(event.groupIndex, group)
        }
    }

    private fun handleGroupRemoved(event: ConnectEvent.GroupRemoved) {
        val device = event.chainPath.resolveRootChain().resolve(event.devicePath) ?: return
        when (device) {
            is GroupChainDevice -> device.removeGroupInternal(event.groupIndex)
            is MultiGroupChainDevice -> device.removeGroupInternal(event.groupIndex)
        }
    }

    private fun handleGroupReordered(event: ConnectEvent.GroupReordered) {
        val device = event.chainPath.resolveRootChain().resolve(event.devicePath) ?: return
        when (device) {
            is GroupChainDevice -> {
                val groups = device.state.value.groups.move(event.fromIndex, event.toIndex) ?: return
                device.state.update { state ->
                    state.copy(
                        groups = groups,
                        openedGroupIndex = event.toIndex.coerceInGroupIndices(groups)
                    )
                }
            }

            is MultiGroupChainDevice -> {
                val groups = device.state.value.groups.move(event.fromIndex, event.toIndex) ?: return
                device.state.update { state ->
                    state.copy(
                        groups = groups,
                        openedGroupIndex = event.toIndex.coerceInGroupIndices(groups),
                        currentMultiIndex = state.currentMultiIndex.coerceInGroupIndices(groups)
                    )
                }
            }
        }
    }

    private fun handleGroupRenamed(event: ConnectEvent.GroupRenamed) {
        val device = event.chainPath.resolveRootChain().resolve(event.devicePath) ?: return
        when (device) {
            is GroupChainDevice -> device.state.update { state ->
                if (event.groupIndex !in state.groups.indices) {
                    state
                } else {
                    state.copy(
                        groups = state.groups.toMutableList().apply {
                            this[event.groupIndex] = this[event.groupIndex].copy(name = event.newName)
                        }
                    )
                }
            }

            is MultiGroupChainDevice -> device.state.update { state ->
                if (event.groupIndex !in state.groups.indices) {
                    state
                } else {
                    state.copy(
                        groups = state.groups.toMutableList().apply {
                            this[event.groupIndex] = this[event.groupIndex].copy(name = event.newName)
                        }
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setDeviceState(device: GenericChainDevice<*>, state: DeviceState) {
        (device as GenericChainDevice<DeviceState>).state.value = state
    }

    private fun restoreNestedState(device: GenericChainDevice<*>, state: DeviceState): Boolean {
        when {
            device is GroupChainDevice && state is GroupChainDeviceState -> {
                device.loadFromState(state)
                device.onStateRestored()
                return true
            }

            device is MultiGroupChainDevice && state is MultiGroupChainDeviceState -> {
                device.loadFromState(state)
                device.onStateRestored()
                return true
            }

            device is ChokeChainDevice && state is ChokeChainDeviceState -> {
                val chain = state.stateChain.unpack()
                chain.signalExit = { signal -> device.signalExit?.invoke(signal) }
                device.state.value = state.copy(chain = chain)
                device.onStateRestored()
                return true
            }
        }

        return false
    }

    private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T>? {
        if (fromIndex !in indices || toIndex !in 0..size) return null
        val mutable = toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex.coerceIn(0, mutable.size), item)
        return mutable
    }

    private fun Int.coerceInGroupIndices(groups: List<Group>): Int =
        if (groups.isEmpty()) 0 else coerceIn(groups.indices)

    private fun requestResync() {
        val userId = provider.localUser.value?.id ?: return
        scope.launch {
            provider.send(ConnectEvent.RequestResync(userId))
        }
    }
}
