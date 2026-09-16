package dev.anthonyhfm.amethyst.workspace

import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ViewportRepository {
    private val _devices = MutableStateFlow<List<LaunchpadViewportElement>>(emptyList())
    val devices: StateFlow<List<LaunchpadViewportElement>> = _devices.asStateFlow()

    fun setDevices(newDevices: List<LaunchpadViewportElement>) {
        _devices.value = newDevices
        Heaven.devices = newDevices
    }

    fun addDevice(device: LaunchpadViewportElement) {
        _devices.update { it + device }
        Heaven.devices = _devices.value
    }

    fun removeDevice(uuid: String) {
        _devices.update { it.filter { device -> device.selectionUUID != uuid && device.launchpadId != uuid } }
        Heaven.devices = _devices.value
    }

    fun clear() {
        _devices.value = emptyList()
        Heaven.devices = emptyList()
    }
}
