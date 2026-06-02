package dev.anthonyhfm.amethyst.core.network.sync

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DeviceSyncReceiver(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return

        job = scope.launch {
            provider.events.collect { event ->
                when (event) {
                    is ConnectEvent.DevicePlaced -> {
                        handleDevicePlaced(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.DeviceRemoved -> {
                        handleDeviceRemoved(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.DeviceMoved -> {
                        handleDeviceMoved(event)
                        WorkspaceSyncCoordinator.triggerVerification()
                    }
                    is ConnectEvent.DevicePropertyChanged -> {
                        handleDevicePropertyChanged(event)
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

    private suspend fun handleDevicePlaced(event: ConnectEvent.DevicePlaced) {
        val element = ViewportDeviceFactory.create(
            type = event.deviceType,
            id = event.deviceId,
            position = Offset(event.positionX, event.positionY)
        )
        WorkspaceRepository.addVirtualDevice(element, fromRemote = true)
    }

    private suspend fun handleDeviceRemoved(event: ConnectEvent.DeviceRemoved) {
        WorkspaceRepository.removeVirtualDeviceById(event.deviceId, fromRemote = true)
    }

    private suspend fun handleDeviceMoved(event: ConnectEvent.DeviceMoved) {
        WorkspaceRepository.moveVirtualDevice(
            deviceId = event.deviceId,
            position = Offset(event.positionX, event.positionY),
            fromRemote = true
        )
    }

    private suspend fun handleDevicePropertyChanged(event: ConnectEvent.DevicePropertyChanged) {
        when (event.property) {
            ConnectEvent.DeviceProperty.ROTATION -> {
                event.value.toFloatOrNull()?.let { rotation ->
                    WorkspaceRepository.updateVirtualDeviceRotation(
                        deviceId = event.deviceId,
                        rotationDegrees = rotation,
                        fromRemote = true
                    )
                }
            }
            ConnectEvent.DeviceProperty.STYLE -> {
                val element = dev.anthonyhfm.amethyst.core.engine.heaven.Heaven.devices.firstOrNull { it.launchpadId == event.deviceId || it.selectionUUID == event.deviceId }
                if (element is LaunchpadViewportElement) {
                    element.applyNetworkStyle(event.value)
                    WorkspaceRepository.deviceRefresh.emit(Unit)
                }
            }
        }
    }
}
