package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

class CoordinateFilterWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Coordinate-Filter Picker"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    var onVirtualDeviceDragStart: ((x: Int, y: Int) -> Unit)? = null
    var onVirtualDeviceDrag: ((x: Int, y: Int) -> Unit)? = null
    var onVirtualDeviceDragEnd: (() -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    fun virtualDeviceDragStart(x: Int, y: Int) {
        onVirtualDeviceDragStart?.invoke(x, y)
    }

    fun virtualDeviceDrag(x: Int, y: Int) {
        onVirtualDeviceDrag?.invoke(x, y)
    }

    fun virtualDeviceDragEnd() {
        onVirtualDeviceDragEnd?.invoke()
    }

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }
}