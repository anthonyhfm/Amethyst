package dev.anthonyhfm.amethyst.core.engine.elements

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.network.sync.ChainSyncCoordinator
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.workspace.chain.ui.SignalIndicatorManager

class Chain : SignalReceiver() {
    val devices: MutableState<List<GenericChainDevice<*>>> = mutableStateOf(emptyList())

    override fun signalEnter(n: List<Signal>) {
        SignalIndicatorManager.trigger(this@Chain, 0)

        if (devices.value.isEmpty()) {
            signalExit?.invoke(n)
        } else {
            devices.value[0].signalEnter(n)
        }
    }

    fun reroute() {
        if (devices.value.isEmpty()) {
            return
        }

        for (i in 0 until devices.value.lastIndex) {
            val current = devices.value[i]
            val next = devices.value[i + 1]
            current.signalExit = { signals ->
                SignalIndicatorManager.trigger(this@Chain, i + 1)
                next.signalEnter(signals)
            }
        }
        devices.value.last().signalExit = { signals ->
            SignalIndicatorManager.trigger(this@Chain, devices.value.size)
            signalExit?.invoke(signals)
        }
    }

    fun add(device: GenericChainDevice<*>, atIndex: Int? = null, fromUser: Boolean = true) {
        val current = devices.value.toMutableList()
        val insertIndex = atIndex?.coerceIn(0, current.size) ?: current.size
        current.add(insertIndex, device)
        devices.value = current
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
            devices.value = devices.value.toMutableList().apply { removeAt(index) }
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
            devices.value = devices.value.toMutableList().apply { removeAll { it.selectionUUID == uuid } }
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
