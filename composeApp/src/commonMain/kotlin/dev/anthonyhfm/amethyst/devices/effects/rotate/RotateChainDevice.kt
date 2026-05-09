package dev.anthonyhfm.amethyst.devices.effects.rotate

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
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class RotateChainDevice : LEDChainDevice<RotateChainDeviceState>() {
    override val state = MutableStateFlow(RotateChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Rotate",
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

    @Composable
    private fun ModeSelectField(
        selectedMode: RotateChainDeviceState.RotateMode,
        onModeSelected: (RotateChainDeviceState.RotateMode) -> Unit,
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
                RotateChainDeviceState.RotateMode.entries.forEach { mode ->
                    SelectItem(
                        text = mode.label,
                        selected = mode == selectedMode,
                        onClick = { onModeSelected(mode) },
                    )
                }
            }
        }
    }

    companion object : ChainDeviceFactory<RotateChainDeviceState> {
        override val stateClass = RotateChainDeviceState::class
        override val serializer = RotateChainDeviceState.serializer()
        override fun create() = RotateChainDevice()
    }
}

private val RotateChainDeviceState.RotateMode.label: String
    get() = when (this) {
        RotateChainDeviceState.RotateMode.DEGREES_90 -> "90°"
        RotateChainDeviceState.RotateMode.DEGREES_180 -> "180°"
        RotateChainDeviceState.RotateMode.DEGREES_270 -> "270°"
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
