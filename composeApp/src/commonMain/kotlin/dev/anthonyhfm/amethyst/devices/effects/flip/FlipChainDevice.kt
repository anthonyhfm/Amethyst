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
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
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
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
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
        val signals = mutableListOf<Signal.LED>()

        if (state.value.bypass) {
            signals.addAll(n.map { it.copy() })
        }

        signals.addAll(n.flatMap { signal ->
            if (signal.x + signal.y * 10 == 100) {
                return@flatMap if (state.value.bypass) emptyList() else listOf(signal.copy())
            }

            var x = signal.x
            var y = signal.y

            when (state.value.mode) {
                FlipChainDeviceState.FlipMode.HORIZONTAL -> x = 9 - x
                FlipChainDeviceState.FlipMode.VERTICAL -> y = 9 - y
                FlipChainDeviceState.FlipMode.DIAGONAL_PLUS -> {
                    val temp = x
                    x = y
                    y = temp
                }
                FlipChainDeviceState.FlipMode.DIAGONAL_MINUS -> {
                    x = 9 - x
                    y = 9 - y
                    val temp = x
                    x = y
                    y = temp
                }
            }

            listOf(signal.copy(x = x, y = y))
        })

        signalExit?.invoke(signals)
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
    val mode: FlipMode = FlipMode.HORIZONTAL,
) : DeviceState() {
    enum class FlipMode {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL_PLUS,
        DIAGONAL_MINUS,
    }
}
