package dev.anthonyhfm.amethyst.devices.effects.macro_filter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.RelativeAlignment
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import kotlin.math.floor
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Tooltip
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionBorder
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class MacroFilterChainDevice : GenericChainDevice<MacroFilterChainDeviceState>() {
    override val state = MutableStateFlow(MacroFilterChainDeviceState())
    override val helpRef = "MacroFilter"

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val macros by WorkspaceRepository.macros.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Macro Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(300.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (macros.isEmpty()) {
                    Text(
                        text = "No macros available",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        textAlign = TextAlign.Center,
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground]
                    )
                    return@ChainDeviceShell
                }

                Box(
                    modifier = Modifier
                        .width(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    var beforeMacro = deviceState.macro
                    Dial(
                        title = "Macro",
                        value = deviceState.macro,
                        type = DialType.Steps(IntArray(macros.size) { it }.toList()),
                        text = "${deviceState.macro + 1}",
                        onStartValueChange = { beforeMacro = it },
                        onResolveTextValue = { text ->
                            text.trim().toIntOrNull()?.let { v ->
                                if (v in 1..macros.size) {
                                    state.update { it.copy(macro = v - 1) }
                                }
                            }
                        },
                        onFinishValueChange = {
                            pushStateChange(state.value.copy(macro = beforeMacro), state.value)
                        },
                        onValueChange = { value ->
                            state.update { it.copy(macro = value) }
                        },
                        enabled = macros.size > 1
                    )
                }

                ValueGrid(deviceState, currentMacroValue = macros.getOrNull(deviceState.macro)?.value)
            }
        }
    }

    @Composable
    private fun ValueGrid(deviceState: MacroFilterChainDeviceState, currentMacroValue: Int?) {
        var gridSize by remember { mutableStateOf(IntSize.Zero) }
        var dragMode by remember { mutableStateOf<Boolean?>(null) }
        var stateBeforeDrag by remember { mutableStateOf(deviceState) }
        var visitedCells by remember { mutableStateOf(emptySet<Int>()) }

        Tooltip(
            text = "Hover over the grid to display the macro value number, and click or drag to select or deselect values for filtering. When processing with values colored in primary color, the incoming signals will pass through the device. Otherwise, they will be blocked.",
            placement = RelativeAlignment.TopCenter,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .aspectRatio(1f)
                    .onGloballyPositioned { gridSize = it.size }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        event.changes.firstOrNull()?.let { change ->
                                            val cellIndex =
                                                calculateCellFromOffset(change.position, gridSize) ?: return@let
                                            stateBeforeDrag = state.value
                                            visitedCells = setOf(cellIndex)
                                            val activating = cellIndex !in state.value.allowedValues
                                            dragMode = activating
                                            val newAllowed = if (activating) {
                                                state.value.allowedValues + cellIndex
                                            } else {
                                                state.value.allowedValues - cellIndex
                                            }
                                            state.update { it.copy(allowedValues = newAllowed) }
                                            change.consume()
                                        }
                                    }

                                    PointerEventType.Move -> {
                                        val mode = dragMode
                                        if (mode != null) {
                                            event.changes.firstOrNull()?.let { change ->
                                                val cellIndex =
                                                    calculateCellFromOffset(change.position, gridSize) ?: return@let
                                                if (cellIndex !in visitedCells) {
                                                    visitedCells = visitedCells + cellIndex
                                                    val newAllowed = if (mode) {
                                                        state.value.allowedValues + cellIndex
                                                    } else {
                                                        state.value.allowedValues - cellIndex
                                                    }
                                                    state.update { it.copy(allowedValues = newAllowed) }
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }

                                    PointerEventType.Release -> {
                                        if (dragMode != null) {
                                            pushStateChange(stateBeforeDrag, state.value)
                                            dragMode = null
                                            visitedCells = emptySet()
                                        }
                                    }
                                }
                            }
                        }
                    }
            ) {
                for (row in 0..9) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (col in 0..9) {
                            val cellValue = row * 10 + col
                            val enabled = cellValue in deviceState.allowedValues
                            val isCurrentMacroValue = cellValue == currentMacroValue
                            val cellShape = RoundedCornerShape(2.dp)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(cellShape)
                                    .background(
                                        if (enabled) Theme[colors][primary] else Theme[colors][muted]
                                    )
                                    .border(
                                        width = if (isCurrentMacroValue) 2.dp else 1.dp,
                                        color = if (isCurrentMacroValue) Theme[colors][selectionSurface] else Theme[colors][border],
                                        shape = cellShape,
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cellValue.toString(),
                                    style = TextStyle(fontSize = 7.sp),
                                    color = if (enabled) Theme[colors][primaryForeground] else Theme[colors][mutedForeground],
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateCellFromOffset(offset: Offset, gridSize: IntSize): Int? {
        if (gridSize.width == 0 || gridSize.height == 0) return null
        val col = floor(offset.x / (gridSize.width.toFloat() / 10)).toInt()
        val row = floor(offset.y / (gridSize.height.toFloat() / 10)).toInt()
        if (col !in 0..9 || row !in 0..9) return null
        return row * 10 + col
    }

    override fun signalEnter(n: List<Signal>) {
        val macros = WorkspaceRepository.macros.value
        if (macros.isEmpty()) {
            signalExit?.invoke(n)
            return
        }
        val macroValue = macros.getOrNull(state.value.macro)?.value ?: return
        if (macroValue in state.value.allowedValues) {
            signalExit?.invoke(n)
        }
    }

    companion object : ChainDeviceFactory<MacroFilterChainDeviceState> {
        override val stateClass = MacroFilterChainDeviceState::class
        override val serializer = MacroFilterChainDeviceState.serializer()
        override fun create() = MacroFilterChainDevice()
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MacroFilterChainDeviceState(
    val macro: Int = 0,
    @ProtoNumber(3)
    val allowedValues: Set<Int> = emptySet(),
) : DeviceState()
