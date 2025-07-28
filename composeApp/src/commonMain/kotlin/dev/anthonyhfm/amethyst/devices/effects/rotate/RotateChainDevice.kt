package dev.anthonyhfm.amethyst.devices.effects.rotate

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
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

class RotateChainDevice : ChainDevice<RotateChainDeviceState>() {
    override val state = MutableStateFlow(RotateChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Rotate",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(140.dp)
        ) {
            Column {
                InputChip(
                    modifier = Modifier
                        .width(100.dp),
                    selected = deviceState.mode == RotateChainDeviceState.RotateMode.DEGREES_90,
                    onClick = {
                        state.update {
                            it.copy(mode = RotateChainDeviceState.RotateMode.DEGREES_90)
                        }
                    },
                    label = {
                        Text(
                            text = "90°",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                )

                InputChip(
                    modifier = Modifier
                        .width(100.dp),
                    selected = deviceState.mode == RotateChainDeviceState.RotateMode.DEGREES_180,
                    onClick = {
                        state.update {
                            it.copy(mode = RotateChainDeviceState.RotateMode.DEGREES_180)
                        }
                    },
                    label = {
                        Text(
                            text = "180°",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                )

                InputChip(
                    modifier = Modifier
                        .width(100.dp),
                    selected = deviceState.mode == RotateChainDeviceState.RotateMode.DEGREES_270,
                    onClick = {
                        state.update {
                            it.copy(mode = RotateChainDeviceState.RotateMode.DEGREES_270)
                        }
                    },
                    label = {
                        Text(
                            text = "270°",
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
                            state.update {
                                it.copy(bypass = checked)
                            }
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

    override fun midiEnter(n: List<Signal>) {
        val bounds = WorkspaceRepository.bounds

        when (state.value.mode) {
            RotateChainDeviceState.RotateMode.DEGREES_270 -> {
                midiExit?.invoke(
                    n.map {
                        it.copy(
                            x = it.y,
                            y = it.x,
                        )
                    }
                )
            }

            else -> {
                midiExit?.invoke(n)
            }
        }

        if (state.value.bypass) {
            midiExit?.invoke(n)
        }
    }
}

@Serializable
data class RotateChainDeviceState(
    val bypass: Boolean = false,
    val mode: RotateMode = RotateMode.DEGREES_90,
) : DeviceState() {
    enum class RotateMode {
        DEGREES_90,
        DEGREES_180,
        DEGREES_270,
    }
}
