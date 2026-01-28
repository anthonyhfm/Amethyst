package dev.anthonyhfm.amethyst.devices.effects.rotate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import dev.anthonyhfm.amethyst.ui.components.DropdownSelect
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
import dev.anthonyhfm.amethyst.ui.components.AmethystCheckbox
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class RotateChainDevice : LEDChainDevice<RotateChainDeviceState>() {
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
            Column(
                modifier = Modifier
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropdownSelect(
                    label = "Mode",
                    options = RotateChainDeviceState.RotateMode.entries,
                    selectedOption = deviceState.mode,
                    onOptionSelected = { mode ->
                        val before = state.value
                        state.update { it.copy(mode = mode) }
                        pushStateChange(before, state.value)
                    },
                    optionToString = {
                        when (it) {
                            RotateChainDeviceState.RotateMode.DEGREES_90 -> "90°"
                            RotateChainDeviceState.RotateMode.DEGREES_180 -> "180°"
                            RotateChainDeviceState.RotateMode.DEGREES_270 -> "270°"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmethystCheckbox(
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
        val rightEdgeX = bounds.first.x + bounds.second.width - 1
        val bottomEdgeY = bounds.first.y + bounds.second.height - 1

        val rotatedSignals = n.map {
            when (state.value.mode) {
                RotateChainDeviceState.RotateMode.DEGREES_90 -> {
                    val relativeX = it.x - bounds.first.x
                    val relativeY = it.y - bounds.first.y

                    val rotatedRelativeX = relativeY
                    val rotatedRelativeY = bounds.second.width - 1 - relativeX

                    val rotatedX = bounds.first.x + rotatedRelativeX
                    val rotatedY = bounds.first.y + rotatedRelativeY

                    it.copy(x = rotatedX, y = rotatedY)
                }

                RotateChainDeviceState.RotateMode.DEGREES_180 -> {
                    val distanceFromRight = rightEdgeX - it.x
                    val distanceFromBottom = bottomEdgeY - it.y

                    val rotatedX = bounds.first.x + distanceFromRight
                    val rotatedY = bounds.first.y + distanceFromBottom

                    it.copy(x = rotatedX, y = rotatedY)
                }

                RotateChainDeviceState.RotateMode.DEGREES_270 -> {
                    val relativeX = it.x - bounds.first.x
                    val relativeY = it.y - bounds.first.y

                    val rotatedRelativeX = bounds.second.height - 1 - relativeY
                    val rotatedRelativeY = relativeX

                    val rotatedX = bounds.first.x + rotatedRelativeX
                    val rotatedY = bounds.first.y + rotatedRelativeY

                    it.copy(x = rotatedX, y = rotatedY)
                }
            }
        }

        signalExit?.invoke(rotatedSignals.toMutableList().apply {
            if (state.value.bypass) {
                addAll(n)
            }
        })
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
