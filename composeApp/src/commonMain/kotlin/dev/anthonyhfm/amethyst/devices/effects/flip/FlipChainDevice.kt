package dev.anthonyhfm.amethyst.devices.effects.flip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class FlipChainDevice : LEDChainDevice<FlipChainDeviceState>() {
    override val state = MutableStateFlow(FlipChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Flip",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(140.dp)
        ) {
            Column {
                InputChip(
                    modifier = Modifier
                        .width(100.dp),
                    selected = deviceState.mode == FlipChainDeviceState.FlipMode.HORIZONTAL,
                    onClick = {
                        val before = state.value
                        state.update {
                            it.copy(mode = FlipChainDeviceState.FlipMode.HORIZONTAL)
                        }

                        pushStateChange(before, state.value)
                    },
                    label = {
                        Text(
                            text = "Horizontal",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                )

                InputChip(
                    modifier = Modifier
                        .width(100.dp),
                    selected = deviceState.mode == FlipChainDeviceState.FlipMode.VERTICAL,
                    onClick = {
                        val before = state.value
                        state.update {
                            it.copy(mode = FlipChainDeviceState.FlipMode.VERTICAL)
                        }

                        pushStateChange(before, state.value)
                    },
                    label = {
                        Text(
                            text = "Vertical",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
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
                    )

                    Text(
                        text = "Bypass",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val bounds = WorkspaceRepository.bounds
        
        signalExit?.invoke(
            n.map {
                when (state.value.mode) {
                    FlipChainDeviceState.FlipMode.HORIZONTAL -> {
                        val rightEdgeX = bounds.first.x + bounds.second.width - 1
                        val distanceFromRight = rightEdgeX - it.x
                        val flippedX = bounds.first.x + distanceFromRight
                        it.copy(x = flippedX)
                    }

                    FlipChainDeviceState.FlipMode.VERTICAL -> {
                        val bottomEdgeY = bounds.first.y + bounds.second.height - 1
                        val distanceFromBottom = bottomEdgeY - it.y
                        val flippedY = bounds.first.y + distanceFromBottom
                        it.copy(y = flippedY)
                    }
                }
            }.toMutableList().apply {
                if (state.value.bypass) {
                    addAll(n)
                }
            }
        )
    }
}

@Serializable
data class FlipChainDeviceState(
    val bypass: Boolean = false,
    val mode: FlipMode = FlipMode.HORIZONTAL,
) : DeviceState() {
    enum class FlipMode {
        VERTICAL,
        HORIZONTAL,
    }
}