package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
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
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.AutoPlayState
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

    var launchpadId: String = UUID.randomUUID()

    val rotationDegrees = mutableFloatStateOf(0f)

    val renderScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))

    var deviceConfig: ProjectDeviceConfig = ProjectDeviceConfig()
    var savedInputPortId: String? = null
    var savedInputPortName: String? = null
    var savedOutputPortId: String? = null
    var savedOutputPortName: String? = null
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
        LaunchpadViewportElementActions(this)
    }

    open val hasStyleOptions: Boolean = false

    /**
     * Called by the network layer when a remote peer changes this device's style.
     * Subclasses with style options should override this to apply the given [key].
     */
    open fun applyNetworkStyle(key: String) {}

    @Composable
    open fun StyleConfigContent(onDismiss: () -> Unit) {}

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
            else -> {
                if (mode.claimInputs) {
                    val localX = translatedX - position.value.x.toInt()
                    val localY = translatedY - position.value.y.toInt()
                    val pitch = (9 - localY) * 10 + localX
                    
                    val offset = position.value.copy(
                        x = position.value.x - layout.offsetX,
                        y = position.value.y - layout.offsetY
                    )
                    
                    val data = dev.anthonyhfm.amethyst.core.midi.data.MidiInputData(
                        pitch = pitch,
                        velocity = 127
                    )
                    mode.onMidiInput(data, offset).invoke()
                } else {
                    sendGenericPadDown(translatedX, translatedY)
                }
            }
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
                if (mode.claimInputs) {
                    val localX = translatedX - position.value.x.toInt()
                    val localY = translatedY - position.value.y.toInt()
                    val pitch = (9 - localY) * 10 + localX
                    
                    val offset = position.value.copy(
                        x = position.value.x - layout.offsetX,
                        y = position.value.y - layout.offsetY
                    )
                    
                    val previousPad = lastDragPad
                    if (previousPad != Pair(translatedX, translatedY)) {
                        previousPad?.let { (oldX, oldY) ->
                            val oldLocalX = oldX - position.value.x.toInt()
                            val oldLocalY = oldY - position.value.y.toInt()
                            val oldPitch = (9 - oldLocalY) * 10 + oldLocalX
                            val oldData = dev.anthonyhfm.amethyst.core.midi.data.MidiInputData(
                                pitch = oldPitch,
                                velocity = 0
                            )
                            mode.onMidiInput(oldData, offset).invoke()
                        }
                        
                        val data = dev.anthonyhfm.amethyst.core.midi.data.MidiInputData(
                            pitch = pitch,
                            velocity = 127
                        )
                        mode.onMidiInput(data, offset).invoke()
                        lastDragPad = Pair(translatedX, translatedY)
                    }
                } else {
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
    }

    open fun handlePadDragEnd() {
        val mode = WorkspaceRepository.mode.value

        if (mode is WorkspaceContract.WorkspaceMode.Layout) return

        when (mode) {
            is KeyframesWorkspaceMode -> mode.virtualDeviceDragEnd()
            is CoordinateFilterWorkspaceMode -> mode.virtualDeviceDragEnd()
            else -> {
                if (mode.claimInputs) {
                    lastDragPad?.let { (x, y) ->
                        val localX = x - position.value.x.toInt()
                        val localY = y - position.value.y.toInt()
                        val pitch = (9 - localY) * 10 + localX
                        
                        val offset = position.value.copy(
                            x = position.value.x - layout.offsetX,
                            y = position.value.y - layout.offsetY
                        )
                        
                        val data = dev.anthonyhfm.amethyst.core.midi.data.MidiInputData(
                            pitch = pitch,
                            velocity = 0
                        )
                        mode.onMidiInput(data, offset).invoke()
                    }
                } else {
                    lastDragPad?.let { (x, y) ->
                        sendGenericPadUp(x, y)
                    }
                }
            }
        }

        lastDragPad = null
    }

    private fun translateToDeviceCoordinates(x: Int, y: Int): Pair<Int, Int> {
        val (rotatedX, rotatedY) = rotatePadCoordinates(x, y, layout, rotationDegrees.floatValue)
        return Pair(
            rotatedX + position.value.x.toInt(),
            (layout.rows - 1) - rotatedY + position.value.y.toInt()
        )
    }

    fun sendGenericPadDown(x: Int, y: Int) {
        if (onCapturePad != null) {
            onCapturePad?.invoke(Triple(true, x, y))
            return
        }

        if (AutomappingManager.isMappingActive()) {
            AutomappingManager.tryCommitPadMapping(
                device = this,
                globalX = x,
                globalY = y,
            )
            return
        }

        WorkspaceRepository.lightsChain.signalEnter(
            Signal.LED(
                origin = this,
                x = x,
                y = y,
                color = Color.White,
                layer = 0
            )
        )

        val midiSignals = listOf(
            Signal.Midi(
                origin = this,
                x = x,
                y = y,
                velocity = 127
            )
        )

        WorkspaceRepository.samplingChain.signalEnter(midiSignals)
        AutoPlayRepository.onMidiInput(midiSignals)
    }

    fun sendGenericPadUp(x: Int, y: Int) {
        if (onCapturePad != null) {
            onCapturePad?.invoke(Triple(false, x, y))
            return
        }

        if (AutomappingManager.isMappingActive()) {
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

        val midiSignals = listOf(
            Signal.Midi(
                origin = this,
                x = x,
                y = y,
                velocity = 0
            )
        )

        WorkspaceRepository.samplingChain.signalEnter(midiSignals)
        AutoPlayRepository.onMidiInput(midiSignals)
    }
}
