package dev.anthonyhfm.amethyst.devices.effects.mask

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.device_mask_color_layer
import amethyst.composeapp.generated.resources.device_mask_shape_layer
import amethyst.composeapp.generated.resources.device_mask_title
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.isLit
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.NestedChainDevice
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import dev.anthonyhfm.amethyst.devices.effects.group.editor.GroupEditorScaffold
import dev.anthonyhfm.amethyst.devices.parallelDuration
import dev.anthonyhfm.amethyst.devices.timelineDuration
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurfaceRaised
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainView
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.compose.resources.stringResource

class MaskChainDevice : LEDChainDevice<MaskChainDeviceState>(), NestedChainDevice {
    override val state = MutableStateFlow(MaskChainDeviceState())
    override val helpRef = "Mask"

    private data class PixelKey(val x: Int, val y: Int)
    private data class SignalKey(val x: Int, val y: Int, val layer: Int)

    private val maskLock = SynchronizedObject()
    private val shapeSignals = mutableMapOf<SignalKey, Signal.LED>()
    private val colorSignals = mutableMapOf<SignalKey, Signal.LED>()
    private val emittedSignals = mutableMapOf<SignalKey, Signal.LED>()
    private val dirtyPixels = mutableSetOf<PixelKey>()
    private var dispatchDepth = 0

    init {
        wireChains()
    }

    override fun timelineDuration(context: TimelineDurationContext) =
        nestedChains().map { it.timelineDuration(context) }.parallelDuration()

    @Composable
    override fun Content() {
        Content(rememberDragAndDropState())
    }

    @Composable
    fun Content(
        dragAndDropState: DragAndDropState<GenericChainDevice<*>> = rememberDragAndDropState(),
    ) {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == selectionUUID }

        GroupEditorScaffold(
            title = stringResource(Res.string.device_mask_title),
            isSelected = isSelected,
            groupList = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MaskLayerButton(
                        label = stringResource(Res.string.device_mask_color_layer),
                        selected = deviceState.openedLayer == MaskLayer.COLOR,
                        onClick = { openLayer(MaskLayer.COLOR) },
                        modifier = Modifier.weight(1f),
                    )
                    MaskLayerButton(
                        label = stringResource(Res.string.device_mask_shape_layer),
                        selected = deviceState.openedLayer == MaskLayer.SHAPE,
                        onClick = { openLayer(MaskLayer.SHAPE) },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        ) {
            val openedChain = chainFor(deviceState.openedLayer)
            key(deviceState.openedLayer) {
                ChainView(
                    chain = openedChain,
                    dragAndDropState = dragAndDropState,
                    parentSelectionUUID = selectionUUID,
                    showContextMenu = true,
                    showRemoteFocus = false,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }

    @Composable
    private fun MaskLayerButton(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val backgroundColor = if (selected) Theme[colors][secondary] else Theme[chainColorTokens][chainSurfaceRaised]
        val textColor = if (selected) Theme[colors][secondaryForeground] else Theme[colors][mutedForeground]

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(DefaultShape)
                .background(backgroundColor)
                .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
        }
    }

    fun openLayer(layer: MaskLayer) {
        if (state.value.openedLayer != layer) {
            state.update { it.copy(openedLayer = layer) }
        }
    }

    fun chainFor(layer: MaskLayer): Chain = when (layer) {
        MaskLayer.COLOR -> state.value.colorChain
        MaskLayer.SHAPE -> state.value.shapeChain
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        synchronized(maskLock) { dispatchDepth += 1 }

        try {
            state.value.shapeChain.signalEnter(n)
            state.value.colorChain.signalEnter(n)
        } finally {
            val output = synchronized(maskLock) {
                dispatchDepth -= 1
                if (dispatchDepth == 0) flushDirtyPixelsLocked() else emptyList()
            }
            emit(output)
        }
    }

    private fun wireChains() {
        state.value.shapeChain.signalExit = { signals ->
            handleLayerOutput(signals.filterIsInstance<Signal.LED>(), shapeSignals)
        }
        state.value.colorChain.signalExit = { signals ->
            handleLayerOutput(signals.filterIsInstance<Signal.LED>(), colorSignals)
        }
    }

    private fun handleLayerOutput(
        signals: List<Signal.LED>,
        cache: MutableMap<SignalKey, Signal.LED>,
    ) {
        val output = synchronized(maskLock) {
            signals.forEach { signal ->
                val key = SignalKey(signal.x, signal.y, signal.layer)
                dirtyPixels += PixelKey(signal.x, signal.y)
                if (signal.color.isLit() && signal.opacity > 0f) {
                    cache[key] = signal
                } else {
                    cache.remove(key)
                }
            }

            if (dispatchDepth == 0) flushDirtyPixelsLocked() else emptyList()
        }
        emit(output)
    }

    private fun flushDirtyPixelsLocked(): List<Signal.LED> {
        if (dirtyPixels.isEmpty()) return emptyList()

        val output = mutableListOf<Signal.LED>()
        val pixels = dirtyPixels.sortedWith(compareBy(PixelKey::y, PixelKey::x))
        dirtyPixels.clear()

        pixels.forEach { pixel ->
            val maskAlpha = shapeSignals.values
                .asSequence()
                .filter { it.x == pixel.x && it.y == pixel.y }
                .maxOfOrNull { it.color.luminance() * it.opacity.coerceIn(0f, 1f) }
                ?.coerceIn(0f, 1f)
                ?: 0f

            val desired = if (maskAlpha > 0f) {
                colorSignals
                    .filterKeys { it.x == pixel.x && it.y == pixel.y }
                    .mapValues { (_, signal) ->
                        signal.copy(opacity = (signal.opacity * maskAlpha).coerceIn(0f, 1f))
                    }
                    .filterValues { it.opacity > 0f }
            } else {
                emptyMap()
            }

            val previouslyEmitted = emittedSignals.filterKeys { it.x == pixel.x && it.y == pixel.y }
            previouslyEmitted.forEach { (key, previous) ->
                if (key !in desired) {
                    output += previous.copy(color = Color.Black, opacity = 1f)
                    emittedSignals.remove(key)
                }
            }

            desired.forEach { (key, signal) ->
                if (emittedSignals[key] != signal) {
                    output += signal
                    emittedSignals[key] = signal
                }
            }
        }

        return output.sortedWith(compareBy(Signal.LED::y, Signal.LED::x, Signal.LED::layer))
    }

    private fun emit(signals: List<Signal.LED>) {
        if (signals.isNotEmpty()) {
            signalExit?.invoke(signals)
        }
    }

    fun packState(): MaskChainDeviceState = state.value.copy(
        colorStateChain = StateChain.pack(state.value.colorChain),
        shapeStateChain = StateChain.pack(state.value.shapeChain),
    )

    fun loadFromState(savedState: MaskChainDeviceState) {
        val colorChain = savedState.colorStateChain.unpack()
        val shapeChain = savedState.shapeStateChain.unpack()
        state.value = savedState.copy(
            colorChain = colorChain,
            shapeChain = shapeChain,
        )
        synchronized(maskLock) {
            shapeSignals.clear()
            colorSignals.clear()
            emittedSignals.clear()
            dirtyPixels.clear()
            dispatchDepth = 0
        }
        wireChains()
        parentChain?.onDeviceRuntimeStateChanged()
    }

    override fun onStateRestored() {
        super.onStateRestored()
        wireChains()
        parentChain?.onDeviceRuntimeStateChanged()
    }

    override fun nestedChains(): List<Chain> = listOf(
        state.value.colorChain,
        state.value.shapeChain,
    )

    companion object : ChainDeviceFactory<MaskChainDeviceState> {
        override val stateClass = MaskChainDeviceState::class
        override val serializer = MaskChainDeviceState.serializer()
        override fun create() = MaskChainDevice()
        override fun pack(device: GenericChainDevice<MaskChainDeviceState>) =
            (device as MaskChainDevice).packState()
        override fun unpack(state: MaskChainDeviceState) =
            MaskChainDevice().apply { loadFromState(state) }
    }
}

@Serializable
enum class MaskLayer {
    COLOR,
    SHAPE,
}

@Serializable
data class MaskChainDeviceState(
    val openedLayer: MaskLayer = MaskLayer.COLOR,
    @Transient
    val colorChain: Chain = Chain(),
    val colorStateChain: StateChain = StateChain(),
    @Transient
    val shapeChain: Chain = Chain(),
    val shapeStateChain: StateChain = StateChain(),
) : DeviceState()
