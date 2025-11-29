package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.anthonyhfm.amethyst.core.midi.data.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.engine.heaven.Screen
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.LaunchpadPreviewState
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class LaunchpadViewportElement(
    override var position: MutableState<Offset> = mutableStateOf(Offset(0f, 0f)),
) : ViewportElement, Selectable {
    abstract val name: String
    abstract override var shape: Shape
    abstract override var size: Size
    abstract val layout: LaunchpadLayout

    override val selectionUUID: String = UUID.randomUUID()

    val renderScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    var deviceConfig: ProjectDeviceConfig = ProjectDeviceConfig()
    val previewState: LaunchpadPreviewState = LaunchpadPreviewState()

    val screen = Screen()

    init {
        screen.screenExit = { u, c ->
            deviceConfig.launchpadDevice?.sendUpdate(u, c)

            renderScope.launch {
                previewState.sendToPreview(u)
            }
        }
    }

    var onEvent: ((WorkspaceContract.Event) -> Unit)? = null

    @Composable
    override fun Actions(scope: RowScope) {
        scope.apply {
            FilledIconButton(
                onClick = {
                    onEvent?.invoke(WorkspaceContract.Event.OnClickDeviceConfigure(selectionUUID))
                }
            ) {
                Icon(Icons.Default.Settings, null)
            }
        }
    }

    @Composable
    abstract override fun Content()

    private var lastDragPad: Pair<Int, Int>? = null

    open fun handlePadDragStart(
        x: Int,
        y: Int
    ) {
        val (translatedX, translatedY) = translateToDeviceCoordinates(x, y)
        val mode = WorkspaceRepository.mode.value

        if (mode is WorkspaceContract.WorkspaceMode.Layout) return

        lastDragPad = Pair(translatedX, translatedY)

        when (mode) {
            is KeyframesWorkspaceMode -> mode.virtualDeviceDragStart(translatedX, translatedY)
            is CoordinateFilterWorkspaceMode -> mode.virtualDeviceDragStart(translatedX, translatedY)
            else -> sendGenericPadDown(translatedX, translatedY)
        }
    }

    open fun handlePadDrag(
        x: Int,
        y: Int
    ) {
        val (translatedX, translatedY) = translateToDeviceCoordinates(x, y)
        val mode = WorkspaceRepository.mode.value

        if (mode is WorkspaceContract.WorkspaceMode.Layout) return

        when (mode) {
            is KeyframesWorkspaceMode -> mode.virtualDeviceDrag(translatedX, translatedY)
            is CoordinateFilterWorkspaceMode -> mode.virtualDeviceDrag(translatedX, translatedY)
            else -> {
                val previousPad = lastDragPad
                if (previousPad != Pair(translatedX, translatedY)) {
                    previousPad?.let { (oldX, oldY) ->
                        sendGenericPadUp(oldX, oldY)
                    }

                    sendGenericPadDown(translatedX, translatedY)
                    lastDragPad = Pair(translatedX, translatedY)
                }
            }
        }
    }

    open fun handlePadDragEnd() {
        val mode = WorkspaceRepository.mode.value

        if (mode is WorkspaceContract.WorkspaceMode.Layout) return

        when (mode) {
            is KeyframesWorkspaceMode -> mode.virtualDeviceDragEnd()
            is CoordinateFilterWorkspaceMode -> mode.virtualDeviceDragEnd()
            else -> {
                lastDragPad?.let { (x, y) ->
                    sendGenericPadUp(x, y)
                }
            }
        }

        lastDragPad = null
    }

    private fun translateToDeviceCoordinates(x: Int, y: Int): Pair<Int, Int> {
        return Pair(
            x + position.value.x.toInt(),
            (layout.rows - 1) - y + position.value.y.toInt()
        )
    }

    private fun sendGenericPadDown(x: Int, y: Int) {
        WorkspaceRepository.lightsChain.signalEnter(
            Signal.LED(
                origin = this,
                x = x,
                y = y,
                color = Color.White,
                layer = 0
            )
        )

        WorkspaceRepository.samplingChain.signalEnter(
            Signal.Midi(
                origin = this,
                x = x,
                y = y,
                velocity = 127
            )
        )
    }

    private fun sendGenericPadUp(x: Int, y: Int) {
        WorkspaceRepository.lightsChain.signalEnter(
            Signal.LED(
                origin = this,
                x = x,
                y = y,
                color = Color.Black,
                layer = 0
            )
        )

        WorkspaceRepository.samplingChain.signalEnter(
            Signal.Midi(
                origin = this,
                x = x,
                y = y,
                velocity = 0
            )
        )
    }
}