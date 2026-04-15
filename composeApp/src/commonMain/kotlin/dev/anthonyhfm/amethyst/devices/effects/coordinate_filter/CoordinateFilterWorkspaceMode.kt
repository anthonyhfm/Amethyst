package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

class CoordinateFilterWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Coordinate-Filter Picker"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    var onVirtualDeviceDragStart: ((device: LaunchpadViewportElement, localX: Int, localY: Int) -> Unit)? = null
    var onVirtualDeviceDrag: ((device: LaunchpadViewportElement, localX: Int, localY: Int) -> Unit)? = null
    var onVirtualDeviceDragEnd: (() -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isExitKey = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)
        if (isExitKey) {
            modeClose?.invoke()
            return true
        }
        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) = {
        val globalX: Int = data.pitch % 10 + offset.x.toInt()
        val globalY: Int = (data.pitch / 10) + offset.y.toInt()

        if (data.velocity != 0) {
            val device = Heaven.devices.firstOrNull { device ->
                val dx = device.position.value.x.toInt()
                val dy = device.position.value.y.toInt()
                globalX in dx until dx + device.layout.cols &&
                globalY in dy until dy + device.layout.rows
            }
            if (device != null) {
                val localX = globalX - device.position.value.x.toInt()
                val localY = globalY - device.position.value.y.toInt()
                onVirtualDeviceDragStart?.invoke(device, localX, localY)
                onVirtualDeviceDragEnd?.invoke()
            }
        }
    }

    fun virtualDeviceDragStart(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        onVirtualDeviceDragStart?.invoke(device, localX, localY)
    }

    fun virtualDeviceDrag(device: LaunchpadViewportElement, localX: Int, localY: Int) {
        onVirtualDeviceDrag?.invoke(device, localX, localY)
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