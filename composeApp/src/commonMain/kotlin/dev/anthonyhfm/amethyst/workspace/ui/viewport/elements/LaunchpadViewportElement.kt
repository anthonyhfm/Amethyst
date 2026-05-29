package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Cable
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Trash2
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.midi.data.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.engine.heaven.Screen
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Dialog
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.LaunchpadPreviewState
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val actionButtonBg = Color(0xFF3F3F46)
private val actionButtonBgHover = Color(0xFF52525B)
private val deleteButtonBg = Color(0xFFDC2626)
private val deleteButtonBgHover = Color(0xFFEF4444)

@Composable
private fun LaunchpadActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    backgroundColor: Color,
    backgroundHoverColor: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        shape = CircleShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(0.dp),
        borderColor = Color.Unspecified,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (hovered) backgroundHoverColor else backgroundColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = Color.White,
        )
    }
}

abstract class LaunchpadViewportElement(
    override var position: MutableState<Offset> = mutableStateOf(Offset(0f, 0f)),
) : ViewportElement, Selectable {
    abstract val name: String
    abstract override var shape: Shape
    abstract override var size: Size
    abstract val layout: LaunchpadLayout

    override val selectionUUID: String = UUID.randomUUID()

    var launchpadId: String = UUID.randomUUID()

    val rotationDegrees = mutableFloatStateOf(0f)

    val renderScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    var deviceConfig: ProjectDeviceConfig = ProjectDeviceConfig()
    val previewState: LaunchpadPreviewState = LaunchpadPreviewState()

    val screen = Screen()

    init {
        screen.screenExit = { u, c ->
            val rotatedUpdates = rotateMidiUpdates(u, layout, rotationDegrees.floatValue)
            deviceConfig.launchpadDevice?.sendUpdate(rotatedUpdates, c)

            renderScope.launch {
                previewState.sendToPreview(u)
            }
        }
    }

    var onEvent: ((WorkspaceContract.Event) -> Unit)? = null
    var onCapturePad: ((Triple<Boolean, Int, Int>) -> Unit)? = null

    @Composable
    override fun Actions(scope: RowScope) {
        val styleDialogState = rememberDialogState()
        val deleteDialogState = rememberDialogState()

        LaunchpadActionButton(
            onClick = { onEvent?.invoke(WorkspaceContract.Event.OnClickDeviceConfigure(selectionUUID)) },
            icon = Lucide.Cable,
            contentDescription = "Connection",
            backgroundColor = actionButtonBg,
            backgroundHoverColor = actionButtonBgHover,
        )

        /*LaunchpadActionButton(
            onClick = { styleDialogState.visible = true },
            icon = Lucide.Palette,
            contentDescription = "Style",
            backgroundColor = actionButtonBg,
            backgroundHoverColor = actionButtonBgHover,
        )*/

        LaunchpadActionButton(
            onClick = { rotationDegrees.floatValue += 90f },
            icon = Lucide.RotateCw,
            contentDescription = "Rotate 90°",
            backgroundColor = actionButtonBg,
            backgroundHoverColor = actionButtonBgHover,
        )

        LaunchpadActionButton(
            onClick = { deleteDialogState.visible = true },
            icon = Lucide.Trash2,
            contentDescription = "Delete",
            backgroundColor = deleteButtonBg,
            backgroundHoverColor = deleteButtonBgHover,
        )

        Dialog(state = styleDialogState) {
            DialogContent {
                DialogHeader {
                    DialogTitle("Style")
                }
            }
        }

        AlertDialog(
            state = deleteDialogState,
        ) {
            AlertDialogHeader {
                AlertDialogTitle("Delete device?")
                AlertDialogDescription("This will permanently remove \"$name\" from the layout.")
            }

            AlertDialogFooter {
                AlertDialogCancel(
                    onClick = { deleteDialogState.visible = false },
                ) {
                    Text("Cancel")
                }

                AlertDialogAction(
                    onClick = {
                        deleteDialogState.visible = false
                        onEvent?.invoke(WorkspaceContract.Event.OnDeleteDevice(selectionUUID))
                    },
                    variant = ButtonVariant.Destructive,
                ) {
                    Text("Delete")
                }
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
            is KeyframesWorkspaceMode -> mode.virtualDeviceDragStart(
                    device = this,
                    localX = translatedX - position.value.x.toInt(),
                    localY = translatedY - position.value.y.toInt()
                )
            is CoordinateFilterWorkspaceMode -> mode.virtualDeviceDragStart(
                device = this,
                localX = translatedX - position.value.x.toInt(),
                localY = translatedY - position.value.y.toInt()
            )
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
            is KeyframesWorkspaceMode -> mode.virtualDeviceDrag(
                    device = this,
                    localX = translatedX - position.value.x.toInt(),
                    localY = translatedY - position.value.y.toInt()
                )
            is CoordinateFilterWorkspaceMode -> mode.virtualDeviceDrag(
                device = this,
                localX = translatedX - position.value.x.toInt(),
                localY = translatedY - position.value.y.toInt()
            )
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

    fun sendGenericPadDown(x: Int, y: Int) {
        if (onCapturePad != null) {
            onCapturePad?.invoke(Triple(true, x, y))
            return
        }

        AutomappingManager.tryCommitPadMapping(
            device = this,
            globalX = x,
            globalY = y,
        )

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

    fun sendGenericPadUp(x: Int, y: Int) {
        if (onCapturePad != null) {
            onCapturePad?.invoke(Triple(false, x, y))
            return
        }

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
