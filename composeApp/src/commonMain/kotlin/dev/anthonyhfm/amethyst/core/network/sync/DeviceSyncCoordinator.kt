package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

object DeviceSyncCoordinator {
    private var broadcaster: DeviceSyncBroadcaster? = null

    fun attach(broadcaster: DeviceSyncBroadcaster) {
        this.broadcaster = broadcaster
    }

    fun detach(broadcaster: DeviceSyncBroadcaster) {
        if (this.broadcaster === broadcaster) {
            this.broadcaster = null
        }
    }

    fun onDevicePlaced(element: LaunchpadViewportElement) {
        broadcaster?.onDevicePlaced(element)
    }

    fun onDeviceModelChanged(
        element: LaunchpadViewportElement,
        pending: List<ConnectEvent.DeviceStateChanged>,
    ) {
        broadcaster?.onDeviceModelChanged(element = element, pending = pending)
    }

    fun onDeviceRemoved(deviceId: String) {
        broadcaster?.onDeviceRemoved(deviceId)
    }

    fun onDeviceMoved(element: LaunchpadViewportElement) {
        broadcaster?.onDeviceMoved(element)
    }

    fun onDeviceRotationChanged(element: LaunchpadViewportElement) {
        broadcaster?.onDeviceRotationChanged(element)
    }

    fun onDeviceStyleChanged(element: LaunchpadViewportElement, styleName: String) {
        broadcaster?.onDeviceStyleChanged(element, styleName)
    }
}
