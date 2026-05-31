package dev.anthonyhfm.amethyst.core.network.sync

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
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
                    is ConnectEvent.DevicePlaced -> handleDevicePlaced(event)
                    is ConnectEvent.DeviceRemoved -> handleDeviceRemoved(event)
                    is ConnectEvent.DeviceMoved -> handleDeviceMoved(event)
                    is ConnectEvent.DevicePropertyChanged -> handleDevicePropertyChanged(event)
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
        }
    }
}
