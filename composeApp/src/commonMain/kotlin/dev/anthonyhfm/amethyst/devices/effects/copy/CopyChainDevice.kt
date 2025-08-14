package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import io.androidpoet.dropdown.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

class CopyChainDevice : ChainDevice<CopyChainDeviceState>() {
    override val state = MutableStateFlow(CopyChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        var openTypePicker: Boolean by remember { mutableStateOf(false) }
        var openIsolationPicker: Boolean by remember { mutableStateOf(false) }

        AmethystDevice(
            title = "Copy",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(160.dp + 52.dp + (deviceState.offsets.size * 126.dp) +  DividerDefaults.Thickness)
        ) {
            Row {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(160.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    AssistChip(
                        onClick = {
                            openTypePicker = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Copy Type")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = when (state.value.type) {
                                    CopyChainDeviceState.CopyType.STATIC -> "Static"
                                    CopyChainDeviceState.CopyType.INTERPOLATE -> "Interpolate"
                                    CopyChainDeviceState.CopyType.RANDOM -> "Random"
                                }
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = openTypePicker,
                        onDismissRequest = { openTypePicker = false },
                    ) {
                        CopyChainDeviceState.CopyType.entries.forEach { type ->
                            MenuItem(
                                onClick = {
                                    state.value = state.value.copy(type = type)
                                    openTypePicker = false
                                },
                                content = {
                                    Text(
                                        text = when (type) {
                                            CopyChainDeviceState.CopyType.STATIC -> "Static"
                                            CopyChainDeviceState.CopyType.INTERPOLATE -> "Interpolate"
                                            CopyChainDeviceState.CopyType.RANDOM -> "Random"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Isolation",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        AssistChip(
                            onClick = {
                                openIsolationPicker = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Copy Type")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    text = when (state.value.isolate) {
                                        CopyChainDeviceState.IsolationType.NONE -> "None"
                                        CopyChainDeviceState.IsolationType.ISOLATE_8x8 -> "8x8 Isolation"
                                        CopyChainDeviceState.IsolationType.ISOLATE_10x10 -> "Full-Device"
                                    }
                                )
                            }
                        )

                        DropdownMenu(
                            expanded = openIsolationPicker,
                            onDismissRequest = { openIsolationPicker = false },
                        ) {
                            CopyChainDeviceState.IsolationType.entries.forEach { type ->
                                MenuItem(
                                    onClick = {
                                        state.value = state.value.copy(isolate = type)
                                        openIsolationPicker = false
                                    },
                                    content = {
                                        Text(
                                            text = when (type) {
                                                CopyChainDeviceState.IsolationType.NONE -> "None"
                                                CopyChainDeviceState.IsolationType.ISOLATE_8x8 -> "8x8 Isolation"
                                                CopyChainDeviceState.IsolationType.ISOLATE_10x10 -> "Full-Device"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),

                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        TimeDial(
                            headline = "Rate",
                            timing = deviceState.timing,
                            onSelectTiming = { timing, msValue ->
                                state.update {
                                    it.copy(
                                        timing = timing,
                                        delayMs = msValue
                                    )
                                }
                            }
                        )

                        TextDial(
                            headline = "Gate",
                            text = "${(deviceState.gate * 200).toInt()}%",
                            value = deviceState.gate,
                            onValueChange = { value ->
                                state.update {
                                    it.copy(gate = value)
                                }
                            },
                            onResolveTextValue = {
                                val gateText = it.removeSuffix("%").trim().toIntOrNull()

                                gateText?.let { gate ->
                                    if (gate in 0..200) {
                                        state.update {
                                            it.copy(gate = gate / 200f) // Convert to float between 0.0 and 1.0
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .rightClickable {
                                    state.update {
                                        it.copy(gate = 0.5f) // Reset gate to its original state
                                    }
                                },
                        )
                    }
                }

                VerticalDivider()

                Row {
                    deviceState.offsets.forEachIndexed { index, offset ->
                        Offset()
                    }

                    AddOffsetButton(
                        onClick = {
                            state.update {
                                it.copy(
                                    offsets = it.offsets + Pair(0, 0) // Add a new offset with default values
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun AddOffsetButton(
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(52.dp),

            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onClick
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add a new device"
                )
            }
        }
    }

    @Composable
    fun Offset() {
        Column(
            modifier = Modifier
                .width(126.dp)
                .fillMaxHeight()
                .padding(start = 6.dp)
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "Offset",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(42.dp)
                        .clickable { },

                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "up")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = -6.dp),

                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(42.dp)
                            .clickable { },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "up")
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(42.dp)
                            .clickable { },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "up")
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(y = -12.dp)
                        .clip(CircleShape)
                        .size(42.dp)
                        .clickable { },

                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "up")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FilledIconButton(
                onClick = {

                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(4.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Remove Offset")
            }
        }
    }

    override fun midiEnter(n: List<Signal>) {
        n.forEach {
            midiExit?.invoke(listOf(it))
        }

        midiExit?.invoke(n)
    }
}

@Serializable
data class CopyChainDeviceState(
    val type: CopyType = CopyType.STATIC,
    val isolate: IsolationType = IsolationType.NONE,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val offsets: List<Pair<Int, Int>> = emptyList(),
    val delayMs: Int = 0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
) : DeviceState() {
    enum class CopyType {
        STATIC,
        INTERPOLATE,
        RANDOM
    }

    enum class IsolationType {
        NONE,
        ISOLATE_8x8,
        ISOLATE_10x10,
    }
}