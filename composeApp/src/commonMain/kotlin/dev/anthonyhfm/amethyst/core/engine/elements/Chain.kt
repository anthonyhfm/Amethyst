package dev.anthonyhfm.amethyst.core.engine.elements

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.devices.GenericChainDevice

class Chain : SignalReceiver() {
    val devices: MutableState<List<GenericChainDevice<*>>> = mutableStateOf(emptyList())

    override fun signalEnter(input: List<Signal>) {
        if (devices.value.isEmpty()) {
            signalExit?.invoke(input)
        } else {
            devices.value[0].signalEnter(input)
        }
    }

    fun reroute() {
        val list = devices.value
        if (list.isEmpty()) {
            return
        }
        // Verkette alle Geräte in Reihenfolge
        for (i in 0 until list.lastIndex) {
            val current = list[i]
            val next = list[i + 1]
            current.signalExit = { signals ->
                next.signalEnter(signals)
            }
        }
        // Letztes Gerät leitet nach außen weiter
        list.last().signalExit = { signals ->
            signalExit?.invoke(signals)
        }
    }

    fun add(device: GenericChainDevice<*>, atIndex: Int? = null, fromUser: Boolean = true) {
        // Defensive: Index einklammern, falls externe Aufrufer (zukünftig) unvalidierte Werte liefern
        val current = devices.value.toMutableList()
        val insertIndex = atIndex?.let { it.coerceIn(0, current.size) } ?: current.size
        current.add(insertIndex, device)
        devices.value = current

        if (fromUser) {
            UndoManager.addAction(
                UndoableAction.ChainDeviceCreation(
                    parent = this@Chain,
                    device = device
                )
            )
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
                        device = deviceToRemove
                    )
                )
            }

            devices.value = devices.value.toMutableList().apply {
                removeAt(index)
            }
        }

        reroute()
    }

    fun remove(uuid: String, fromUser: Boolean = true) {
        val deviceToRemove = devices.value.find { it.selectionUUID == uuid }

        if (deviceToRemove != null) {
            if (fromUser) {
                UndoManager.addAction(
                    UndoableAction.ChainDeviceRemoval(
                        parent = this,
                        device = deviceToRemove
                    )
                )
            }

            devices.value = devices.value.toMutableList().apply {
                removeAll { it.selectionUUID == uuid }
            }
        } else {
            devices.value.map {
                when (it) {
                    is GroupChainDevice -> {
                        it.apply {
                            state.value.groups.forEach { group ->
                                group.chain.remove(uuid, fromUser)
                            }
                        }
                    }

                    is MultiGroupChainDevice -> {
                        it.apply {
                            state.value.groups.forEach { group ->
                                group.chain.remove(uuid, fromUser)
                            }
                        }
                    }

                    is ChokeChainDevice -> {
                        it.apply {
                            state.value.chain.remove(uuid, fromUser)
                        }
                    }

                    else -> it
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
            when (device) {
                is GroupChainDevice -> {
                    device.state.value.groups.forEach { group ->
                        group.chain.findDeviceChain(deviceUUID)?.let { foundChain ->
                            return foundChain
                        }
                    }
                }

                is MultiGroupChainDevice -> {
                    device.state.value.groups.forEach { group ->
                        group.chain.findDeviceChain(deviceUUID)?.let { foundChain ->
                            return foundChain
                        }
                    }
                }

                is ChokeChainDevice -> {
                    device.state.value.chain.findDeviceChain(deviceUUID)?.let { foundChain ->
                        return foundChain
                    }
                }
            }
        }

        return null
    }
}