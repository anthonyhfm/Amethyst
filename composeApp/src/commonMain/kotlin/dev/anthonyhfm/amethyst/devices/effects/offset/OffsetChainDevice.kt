package dev.anthonyhfm.amethyst.devices.effects.offset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.East
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.West
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class OffsetChainDevice : LEDChainDevice<OffsetChainDeviceState>() {
    override val state = MutableStateFlow(OffsetChainDeviceState())

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Offset",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(156.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Coordinates()

                OffsetButtons()
            }
        }
    }

    @Composable
    private fun Coordinates() {
        val deviceState by state.collectAsState()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoordinateChip(
                label = "X",
                value = deviceState.offsetX,
                modifier = Modifier.weight(1f)
            )

            CoordinateChip(
                label = "Y",
                value = deviceState.offsetY,
                modifier = Modifier.weight(1f)
            )
        }
    }

    @Composable
    private fun OffsetButtons() {
        Column(
            modifier = Modifier.width(124.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectionButton(
                    icon = Icons.Default.NorthWest,
                    contentDescription = "Move up and left",
                    deltaX = -1,
                    deltaY = 1,
                    variant = ButtonVariant.Outline
                )

                DirectionButton(
                    icon = Icons.Default.North,
                    contentDescription = "Move up",
                    deltaY = 1,
                    variant = ButtonVariant.Secondary
                )

                DirectionButton(
                    icon = Icons.Default.NorthEast,
                    contentDescription = "Move up and right",
                    deltaX = 1,
                    deltaY = 1,
                    variant = ButtonVariant.Outline
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DirectionButton(
                    icon = Icons.Default.West,
                    contentDescription = "Move left",
                    deltaX = -1,
                    variant = ButtonVariant.Secondary
                )

                DirectionButton(
                    icon = Icons.Default.East,
                    contentDescription = "Move right",
                    deltaX = 1,
                    variant = ButtonVariant.Secondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectionButton(
                    icon = Icons.Default.SouthWest,
                    contentDescription = "Move down and left",
                    deltaX = -1,
                    deltaY = -1,
                    variant = ButtonVariant.Outline
                )

                DirectionButton(
                    icon = Icons.Default.South,
                    contentDescription = "Move down",
                    deltaY = -1,
                    variant = ButtonVariant.Secondary
                )

                DirectionButton(
                    icon = Icons.Default.SouthEast,
                    contentDescription = "Move down and right",
                    deltaX = 1,
                    deltaY = -1,
                    variant = ButtonVariant.Outline
                )
            }
        }
    }

    @Composable
    private fun CoordinateChip(
        label: String,
        value: Int,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground]
            )

            Text(
                text = value.toString(),
                style = Theme[typography][small],
                color = Theme[colors][accentForeground],
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Theme[colors][accent])
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }

    @Composable
    private fun DirectionButton(
        icon: ImageVector,
        contentDescription: String,
        deltaX: Int = 0,
        deltaY: Int = 0,
        variant: ButtonVariant
    ) {
        Button(
            onClick = { updateOffset(deltaX = deltaX, deltaY = deltaY) },
            variant = variant,
            size = ButtonSize.Icon
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = directionButtonTint(variant)
            )
        }
    }

    @Composable
    private fun directionButtonTint(variant: ButtonVariant): Color = when (variant) {
        ButtonVariant.Secondary -> Theme[colors][secondaryForeground]
        ButtonVariant.Outline -> Theme[colors][foreground]
        else -> Theme[colors][foreground]
    }

    private fun updateOffset(deltaX: Int = 0, deltaY: Int = 0) {
        val previousState = state.value
        val updatedState = previousState.copy(
            offsetX = previousState.offsetX + deltaX,
            offsetY = previousState.offsetY + deltaY
        )

        pushStateChange(previousState, updatedState)
        state.update { updatedState }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val movedSignals = n.map { signal ->
            signal.copy(
                x = signal.x + state.value.offsetX,
                y = signal.y - state.value.offsetY,
            )
        }

        val processedSignals = when (state.value.gridMode) {
            OffsetChainDeviceState.GridMode.NONE -> movedSignals
            OffsetChainDeviceState.GridMode.EDGELESS -> {
                if (state.value.wrap) {
                    movedSignals.map { signal ->
                        signal.copy(
                            x = wrapInRange(signal.x, 1, 8),
                            y = wrapInRange(signal.y, 1, 8),
                        )
                    }
                } else {
                    movedSignals.filter { signal -> signal.x in 1..8 && signal.y in 1..8 }
                }
            }
            OffsetChainDeviceState.GridMode.FULL -> {
                if (state.value.wrap) {
                    movedSignals.map { signal ->
                        signal.copy(
                            x = wrapInRange(signal.x, 0, 9),
                            y = wrapInRange(signal.y, 0, 9),
                        )
                    }
                } else {
                    movedSignals.filter { signal -> signal.x in 0..9 && signal.y in 0..9 }
                }
            }
        }

        if (processedSignals.isNotEmpty()) {
            signalExit?.invoke(processedSignals)
        }
    }

    private fun wrapInRange(value: Int, min: Int, max: Int): Int {
        val size = max - min + 1
        return min + ((value - min).mod(size))
    }
}

@Serializable
data class OffsetChainDeviceState(
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val gridMode: GridMode = GridMode.NONE,
    val wrap: Boolean = false,
) : DeviceState() {
    enum class GridMode {
        NONE,
        EDGELESS,
        FULL,
    }
}
