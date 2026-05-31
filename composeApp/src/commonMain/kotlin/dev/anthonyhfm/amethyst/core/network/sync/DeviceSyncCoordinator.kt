package dev.anthonyhfm.amethyst.core.network.sync

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

    fun onDeviceRemoved(deviceId: String) {
        broadcaster?.onDeviceRemoved(deviceId)
    }

    fun onDeviceMoved(element: LaunchpadViewportElement) {
        broadcaster?.onDeviceMoved(element)
    }

    fun onDeviceRotationChanged(element: LaunchpadViewportElement) {
        broadcaster?.onDeviceRotationChanged(element)
    }
}
