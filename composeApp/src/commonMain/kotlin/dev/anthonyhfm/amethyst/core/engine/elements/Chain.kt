package dev.anthonyhfm.amethyst.core.engine.elements

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.SignalIndicatorManager

open class Chain : SignalReceiver() {
    val devices: MutableState<List<GenericChainDevice<*>>> = mutableStateOf(emptyList())
    internal var topologyChangedListener: (() -> Unit)? = null

    protected open fun onDevicesChanged(
        previous: List<GenericChainDevice<*>>,
        current: List<GenericChainDevice<*>>,
    ) = Unit

    private fun replaceDevices(current: List<GenericChainDevice<*>>) {
        val previous = devices.value
        devices.value = current
        onDevicesChanged(previous, current)
        topologyChangedListener?.invoke()
    }

    override fun signalEnter(n: List<Signal>) {
        SignalIndicatorManager.trigger(this@Chain, 0)

        val firstUnmuted = devices.value.firstOrNull { !it.state.value.isMuted }
        if (firstUnmuted == null) {
            signalExit?.invoke(n)
        } else {
            firstUnmuted.signalEnter(n)
        }
    }

    open fun reroute() {
        val devList = devices.value
        if (devList.isEmpty()) {
            return
        }

        for (i in devList.indices) {
            val current = devList[i]
            var nextUnmutedIndex = -1
            for (j in (i + 1) until devList.size) {
                if (!devList[j].state.value.isMuted) {
                    nextUnmutedIndex = j
                    break
                }
            }

            if (nextUnmutedIndex != -1) {
                val nextDevice = devList[nextUnmutedIndex]
                current.signalExit = { signals ->
                    SignalIndicatorManager.trigger(this@Chain, nextUnmutedIndex)
                    nextDevice.signalEnter(signals)
                }
            } else {
                current.signalExit = { signals ->
                    SignalIndicatorManager.trigger(this@Chain, devList.size)
                    signalExit?.invoke(signals)
                }
            }
        }
    }

    fun add(device: GenericChainDevice<*>, atIndex: Int? = null, fromUser: Boolean = true) {
        val current = devices.value.toMutableList()
        val insertIndex = atIndex?.coerceIn(0, current.size) ?: current.size
        current.add(insertIndex, device)
        replaceDevices(current)
        device.onAddedToChain(parentChain = this)

        if (fromUser) {
            UndoManager.addAction(
                UndoableAction.ChainDeviceCreation(
                    parent = this@Chain,
                    device = device,
                    creationIndex = insertIndex
                )
            )

            ChainSyncCoordinator.onDevicePlaced(this, device, insertIndex)
        }
        reroute()
    }

    fun remove(index: Int, fromUser: Boolean = true) {
        if (index >= 0 && index < devices.value.size) {
            val deviceToRemove = devices.value[index]
            if (fromUser) {
                UndoManager.addAction(
                    UndoableAction.ChainDeviceRemoval(
                        parent = this,
                        device = deviceToRemove,
                        originalIndex = index
                    )
                )

                ChainSyncCoordinator.onDeviceRemoved(this, deviceToRemove.selectionUUID)
            }
            replaceDevices(devices.value.toMutableList().apply { removeAt(index) })
            deviceToRemove.onRemovedFromChain()
        }
        reroute()
    }

    fun remove(uuid: String, fromUser: Boolean = true) {
        val deviceIndex = devices.value.indexOfFirst { it.selectionUUID == uuid }
        val deviceToRemove = devices.value.getOrNull(deviceIndex)
        if (deviceToRemove != null) {
            if (fromUser) {
                UndoManager.addAction(
                    UndoableAction.ChainDeviceRemoval(
                        parent = this,
                        device = deviceToRemove,
                        originalIndex = deviceIndex
                    )
                )

                ChainSyncCoordinator.onDeviceRemoved(this, uuid)
            }
            replaceDevices(devices.value.toMutableList().apply { removeAll { it.selectionUUID == uuid } })
            deviceToRemove.onRemovedFromChain()
        } else {
            devices.value.forEach { device ->
                if (device is NestedChainDevice) {
                    device.nestedChains().forEach { it.remove(uuid, fromUser) }
                }
            }
        }
        reroute()
    }

    internal fun onDeviceRuntimeStateChanged() {
        onDevicesChanged(devices.value, devices.value)
        topologyChangedListener?.invoke()
        reroute()
    }

    fun findDeviceChain(deviceUUID: String): Chain? {
        if (devices.value.any { it.selectionUUID == deviceUUID }) {
            return this
        }

        devices.value.forEach { device ->
            if (device is NestedChainDevice) {
                device.nestedChains().forEach { nested ->
                    nested.findDeviceChain(deviceUUID)?.let { return it }
                }
            }
        }

        return null
    }
}
