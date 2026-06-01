package dev.anthonyhfm.amethyst.devices.effects.flip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class FlipChainDevice : LEDChainDevice<FlipChainDeviceState>() {
    override val state = MutableStateFlow(FlipChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Flip",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(140.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeSelectField(
                    selectedMode = deviceState.mode,
                    onModeSelected = { mode ->
                        val before = state.value
                        state.update { it.copy(mode = mode) }
                        pushStateChange(before, state.value)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Separator()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = deviceState.isolate,
                        onCheckedChange = { checked ->
                            val before = state.value
                            state.update {
                                it.copy(isolate = checked)
                            }

                            pushStateChange(before, state.value)
                        },
                        size = 18.dp,
                        iconSize = 14.dp,
                    )

                    Text(
                        text = "Isolate",
                        style = Theme[typography][small],
                        color = Theme[colors][foreground]
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = deviceState.bypass,
                        onCheckedChange = { checked ->
                            val before = state.value
                            state.update {
                                it.copy(bypass = checked)
                            }

                            pushStateChange(before, state.value)
                        },
                        size = 18.dp,
                        iconSize = 14.dp,
                    )

                    Text(
                        text = "Bypass",
                        style = Theme[typography][small],
                        color = Theme[colors][foreground]
                    )
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val state = state.value

        val flippedSignals = n.map { signal ->
            val bounds = resolveBounds(signal, state.isolate)
            val rightEdgeX = bounds.first.x + bounds.second.width - 1
            val bottomEdgeY = bounds.first.y + bounds.second.height - 1

            when (state.mode) {
                FlipChainDeviceState.FlipMode.HORIZONTAL -> {
                    val flippedX = bounds.first.x + (rightEdgeX - signal.x)

                    signal.copy(x = flippedX)
                }

                FlipChainDeviceState.FlipMode.VERTICAL -> {
                    val flippedY = bounds.first.y + (bottomEdgeY - signal.y)

                    signal.copy(y = flippedY)
                }

                FlipChainDeviceState.FlipMode.DIAGONAL_PLUS -> {
                    val relativeX = signal.x - bounds.first.x
                    val relativeY = signal.y - bounds.first.y

                    val flippedX = bounds.first.x + relativeY
                    val flippedY = bounds.first.y + relativeX

                    signal.copy(x = flippedX, y = flippedY)
                }

                FlipChainDeviceState.FlipMode.DIAGONAL_MINUS -> {
                    val relativeX = signal.x - bounds.first.x
                    val relativeY = signal.y - bounds.first.y

                    val flippedRelativeX = bounds.second.height - 1 - relativeY
                    val flippedRelativeY = bounds.second.width - 1 - relativeX

                    val flippedX = bounds.first.x + flippedRelativeX
                    val flippedY = bounds.first.y + flippedRelativeY

                    signal.copy(x = flippedX, y = flippedY)
                }
            }
        }

        signalExit?.invoke(flippedSignals.toMutableList().apply {
            if (state.bypass) {
                addAll(n)
            }
        })
    }

    private fun resolveBounds(signal: Signal.LED, isolate: Boolean): Pair<IntOffset, IntSize> {
        if (!isolate) return WorkspaceRepository.bounds

        val device = signal.origin as? LaunchpadViewportElement ?: return WorkspaceRepository.bounds
        return Pair(
            first = IntOffset(
                x = device.position.value.x.toInt(),
                y = device.position.value.y.toInt(),
            ),
            second = IntSize(
                width = device.layout.cols,
                height = device.layout.rows,
            )
        )
    }

    @Composable
    private fun ModeSelectField(
        selectedMode: FlipChainDeviceState.FlipMode,
        onModeSelected: (FlipChainDeviceState.FlipMode) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Mode",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )

            Select(
                value = selectedMode.label,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SmallShape,
                triggerHeight = 24.dp,
                triggerContentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                FlipChainDeviceState.FlipMode.entries.forEach { mode ->
                    SelectItem(
                        text = mode.label,
                        selected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
                    )
                }
            }
        }
    }

    companion object : ChainDeviceFactory<FlipChainDeviceState> {
        override val stateClass = FlipChainDeviceState::class
        override val serializer = FlipChainDeviceState.serializer()
        override fun create() = FlipChainDevice()
    }
}

private val FlipChainDeviceState.FlipMode.label: String
    get() = when (this) {
        FlipChainDeviceState.FlipMode.HORIZONTAL -> "Horizontal"
        FlipChainDeviceState.FlipMode.VERTICAL -> "Vertical"
        FlipChainDeviceState.FlipMode.DIAGONAL_PLUS -> "Diagonal+"
        FlipChainDeviceState.FlipMode.DIAGONAL_MINUS -> "Diagonal-"
    }

@Serializable
data class FlipChainDeviceState(
    val bypass: Boolean = false,
    val isolate: Boolean = false,
    val mode: FlipMode = FlipMode.HORIZONTAL,
) : DeviceState() {
    enum class FlipMode {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL_PLUS,
        DIAGONAL_MINUS,
    }
}
