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
import dev.anthonyhfm.amethyst.core.engine.elements.Screen
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

    open fun handleButtonEvent(
        down: Boolean,
        x: Int,
        y: Int
    ) {
        val mode = WorkspaceRepository.mode.value

        if (mode is WorkspaceContract.WorkspaceMode.Layout) return

        when (mode) {
            is KeyframesWorkspaceMode -> {
                if (down) {
                    mode.virtualDevicePress(x + position.value.x.toInt(), (layout.rows - 1) - y + position.value.y.toInt())
                }
            }

            is CoordinateFilterWorkspaceMode -> {
                if (down) {
                    mode.virtualDevicePress(x + position.value.x.toInt(), (layout.rows - 1) - y + position.value.y.toInt())
                }
            }

            else -> {
                WorkspaceRepository.lightsChain.signalEnter(
                    Signal.LED(
                        origin = this,
                        x = x + position.value.x.toInt(),
                        y = (layout.rows - 1) - y + position.value.y.toInt(),
                        color = if (down) Color.White else Color.Black,
                        layer = 0
                    )
                )

                WorkspaceRepository.samplingChain.signalEnter(
                    Signal.Midi(
                        origin = this,
                        x = x + position.value.x.toInt(),
                        y = (layout.rows - 1) - y + position.value.y.toInt(),
                        velocity = if (down) 127 else 0
                    )
                )
            }
        }
    }
}