package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DeviceSyncBroadcaster(
    private val provider: AmethystConnectProvider,
    private val scope: CoroutineScope
) {

    fun onDevicePlaced(element: LaunchpadViewportElement) {
        scope.launch {
            provider.send(
                ConnectEvent.DevicePlaced(
                    deviceId = element.launchpadId,
                    deviceType = element.toViewportDeviceType(),
                    positionX = element.position.value.x,
                    positionY = element.position.value.y
                )
            )
        }
    }

    fun onDeviceRemoved(deviceId: String) {
        scope.launch {
            provider.send(ConnectEvent.DeviceRemoved(deviceId))
        }
    }

    fun onDeviceMoved(element: LaunchpadViewportElement) {
        scope.launch {
            provider.send(
                ConnectEvent.DeviceMoved(
                    deviceId = element.launchpadId,
                    positionX = element.position.value.x,
                    positionY = element.position.value.y
                )
            )
        }
    }

    fun onDeviceRotationChanged(element: LaunchpadViewportElement) {
        scope.launch {
            provider.send(
                ConnectEvent.DevicePropertyChanged(
                    deviceId = element.launchpadId,
                    property = ConnectEvent.DeviceProperty.ROTATION,
                    value = element.rotationDegrees.floatValue.toString()
                )
            )
        }
    }
}
