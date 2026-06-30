package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Event
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.ui.components.AddDeviceButton
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

class CoordinateFilterWorkspaceMode : WorkspaceMode() {
    override val displayName: String = "Coordinate-Filter Picker"
    override val selectableMode: Boolean = false
    override val claimMidiInputs: Boolean = true

    @androidx.compose.runtime.Composable
    override fun Content(modifier: Modifier) {
        val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            WorkspaceViewport(
                viewportKey = "workspace-coordinate-filter",
                config = ViewportConfig(
                    minZoom = 0.5f,
                    maxZoom = 2f,
                    enablePanning = true,
                    enableZoom = true,
                    panBoundsPolicy = ViewportPanBoundsPolicy.ClampToContent(),
                    showGrid = true,
                    showOrigin = true,
                    showRemoteCursors = true,
                    contentPadding = 80.dp
                ),
                onEvent = { viewModel.onEvent(it) }
            )

            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
            ) {
                AddDeviceButton(
                    onClick = {
                        viewModel.onEvent(WorkspaceContract.Event.OpenVirtualDevicePicker)
                    }
                )
            }
        }
    }

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
            requestClose()
            return true
        }
        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) {
        val globalX: Int = data.pitch % 10 + offset.x.toInt()
        val globalY: Int = (9 - (data.pitch / 10)) + offset.y.toInt()

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

    private fun requestClose() {
        modeClose?.invoke()
        WorkspaceRepository.switchToPreviousMode()
    }
}
