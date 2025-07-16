package dev.anthonyhfm.amethyst.devices.effects.offset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class OffsetChainDevice : ChainDevice<OffsetChainDeviceState>() {
    override val state = MutableStateFlow(OffsetChainDeviceState())

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Offset",
            isSelected = selections.contains(this),
            modifier = Modifier
                .width(200.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth(0.6f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "X: ",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                )

                Text(
                    text = "${deviceState.offsetX}",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Y: ",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                )

                Text(
                    text = "${deviceState.offsetY}",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                )
            }
        }
    }

    @Composable
    private fun OffsetButtons() {
        Column(
            modifier = Modifier
                .size(48.dp * 3)
        ) {
            Row {
                IconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetX = it.offsetX - 1,
                                offsetY = it.offsetY + 1
                            )
                        }
                    }
                ) { Icon(Icons.Default.NorthWest, null) }

                FilledIconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetY = it.offsetY + 1
                            )
                        }
                    }
                ) { Icon(Icons.Default.North, null) }

                IconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetX = it.offsetX + 1,
                                offsetY = it.offsetY + 1
                            )
                        }
                    }
                ) { Icon(Icons.Default.NorthEast, null) }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                FilledIconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetX = it.offsetX - 1,
                            )
                        }
                    }
                ) { Icon(Icons.Default.West, null) }

                FilledIconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetX = it.offsetX + 1,
                            )
                        }
                    }
                ) { Icon(Icons.Default.East, null) }
            }
            Row {
                IconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetX = it.offsetX - 1,
                                offsetY = it.offsetY - 1
                            )
                        }
                    }
                ) { Icon(Icons.Default.SouthWest, null) }

                FilledIconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetY = it.offsetY - 1
                            )
                        }
                    }
                ) { Icon(Icons.Default.South, null) }

                IconButton(
                    onClick = {
                        state.update {
                            it.copy(
                                offsetX = it.offsetX + 1,
                                offsetY = it.offsetY - 1
                            )
                        }
                    }
                ) { Icon(Icons.Default.SouthEast, null) }
            }
        }
    }

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(
            n.map {
                it.copy(
                    x = it.x + state.value.offsetX,
                    y = it.y - state.value.offsetY,
                )
            }
        )
    }
}

@Serializable
data class OffsetChainDeviceState(
    val offsetX: Int = 0,
    val offsetY: Int = 0
) : DeviceState()