package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PinchGraph
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

fun copyModeLabel(mode: CopyChainDeviceState.CopyMode): String = when (mode) {
    CopyChainDeviceState.CopyMode.STATIC -> "Static"
    CopyChainDeviceState.CopyMode.ANIMATE -> "Animate"
    CopyChainDeviceState.CopyMode.INTERPOLATE -> "Interpolate"
    CopyChainDeviceState.CopyMode.HOLD_INTERPOLATE -> "Hold Interpol."
    CopyChainDeviceState.CopyMode.RANDOM_SINGLE -> "Random Single"
    CopyChainDeviceState.CopyMode.RANDOM_LOOP -> "Random Loop"
}

fun isolationLabel(mode: CopyChainDeviceState.IsolationType): String = when (mode) {
    CopyChainDeviceState.IsolationType.NONE -> "None"
    CopyChainDeviceState.IsolationType.EDGELESS -> "Edgeless"
    CopyChainDeviceState.IsolationType.FULL -> "Full"
}

@Suppress("DEPRECATION")
fun gridModeLabel(mode: CopyChainDeviceState.GridMode): String = when (mode) {
    CopyChainDeviceState.GridMode.NONE -> "None"
    CopyChainDeviceState.GridMode.EDGELESS -> "Edgeless"
    CopyChainDeviceState.GridMode.FULL -> "Full"
}

@Composable
fun CopyTimeControls(
    timing: Timing,
    onTimingChanged: (Timing) -> Unit,
    gate: Float,
    onGateChanged: (Float) -> Unit,
    pinch: Float,
    onPinchChanged: (Float) -> Unit,
    bilateral: Boolean,
    onToggleBilateral: () -> Unit,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        TimeDial(
            title = "Duration",
            timing = timing,
            onSelectTiming = { newTiming, _ ->
                onTimingChanged(newTiming)
            },
            flat = flat,
        )

        if (flat) {
            FlatDial(
                type = DialType.Continuous,
                title = "Gate",
                text = "${(gate * 200).toInt()}%",
                value = gate,
                onValueChange = onGateChanged,
                onResolveTextValue = { text ->
                    val gateText = text.removeSuffix("%").trim().toIntOrNull()
                    gateText?.let { g ->
                        if (g in 0..200) {
                            onGateChanged(g / 200f)
                        }
                    }
                },
                modifier = Modifier.rightClickable {
                    onGateChanged(0.5f)
                },
            )
        } else {
            Dial(
                type = DialType.Continuous,
                title = "Gate",
                text = "${(gate * 200).toInt()}%",
                value = gate,
                onValueChange = onGateChanged,
                onResolveTextValue = { text ->
                    val gateText = text.removeSuffix("%").trim().toIntOrNull()
                    gateText?.let { g ->
                        if (g in 0..200) {
                            onGateChanged(g / 200f)
                        }
                    }
                },
                modifier = Modifier.rightClickable {
                    onGateChanged(0.5f)
                },
            )
        }

        PinchGraph(
            pinch = pinch,
            onPinchChange = onPinchChanged,
            bilateral = bilateral,
            onToggleBilateral = onToggleBilateral,
            modifier = Modifier.size(52.dp)
        )
    }
}

@Composable
fun <T> CopySelectField(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionToString: (T) -> String = { it.toString() },
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )

        Select(
            value = optionToString(selectedOption),
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
            triggerHeight = 24.dp,
            triggerContentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            options.forEach { option ->
                SelectItem(
                    text = optionToString(option),
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                )
            }
        }
    }
}

@Composable
fun ToggleOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )
    }
}

@Composable
fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Secondary,
) {
    val tint = when (variant) {
        ButtonVariant.Secondary -> Theme[colors][secondaryForeground]
        else -> Theme[colors][foreground]
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        size = ButtonSize.Icon,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
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
        IconActionButton(
            icon = Icons.Default.Add,
            contentDescription = "Add Offset",
            onClick = onClick,
            variant = ButtonVariant.Outline,
        )
    }
}

@Composable
fun CopyOffsetCard(
    index: Int,
    offset: CopyChainDeviceState.Offset,
    deviceState: CopyChainDeviceState,
    onChangeOffset: (CopyChainDeviceState.Offset) -> Unit,
    onRemoveOffset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .fillMaxHeight()
            .padding(start = 4.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color = Theme[colors][secondary])
            .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Offset",
                textAlign = TextAlign.Center,
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )

            IconActionButton(
                icon = Icons.Default.Remove,
                contentDescription = "Remove Offset",
                onClick = onRemoveOffset,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                variant = ButtonVariant.Ghost,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .rightClickable {
                    onChangeOffset(offset.copy(isAbsolute = !offset.isAbsolute))
                }
        ) {
            Text(
                text = if (offset.isAbsolute) "AbsX: ${offset.absoluteX}" else "X: ${offset.x}",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )

            Text(
                text = if (offset.isAbsolute) "AbsY: ${offset.absoluteY}" else "Y: ${offset.y}",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }

        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconActionButton(
                    icon = Icons.Default.ArrowUpward,
                    contentDescription = "Move up",
                    onClick = {
                        if (offset.isAbsolute) {
                            onChangeOffset(offset.copy(absoluteY = offset.absoluteY + 1))
                        } else {
                            onChangeOffset(offset.copy(y = offset.y + 1))
                        }
                    },
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                ) {
                    IconActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move left",
                        onClick = {
                            if (offset.isAbsolute) {
                                onChangeOffset(offset.copy(absoluteX = offset.absoluteX - 1))
                            } else {
                                onChangeOffset(offset.copy(x = offset.x - 1))
                            }
                        },
                    )

                    IconActionButton(
                        icon = Icons.Default.ArrowDownward,
                        contentDescription = "Move down",
                        onClick = {
                            if (offset.isAbsolute) {
                                onChangeOffset(offset.copy(absoluteY = offset.absoluteY - 1))
                            } else {
                                onChangeOffset(offset.copy(y = offset.y - 1))
                            }
                        },
                    )

                    IconActionButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move right",
                        onClick = {
                            if (offset.isAbsolute) {
                                onChangeOffset(offset.copy(absoluteX = offset.absoluteX + 1))
                            } else {
                                onChangeOffset(offset.copy(x = offset.x + 1))
                            }
                        },
                    )
                }
            }
        }

        if (deviceState.mode == CopyChainDeviceState.CopyMode.INTERPOLATE || deviceState.mode == CopyChainDeviceState.CopyMode.HOLD_INTERPOLATE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Dial(
                    type = DialType.Continuous,
                    text = "${offset.angle}°",
                    value = offset.angle.toFloat() / 360f,
                    onValueChange = { value ->
                        onChangeOffset(offset.copy(angle = (value * 360f).toInt()))
                    },
                    onResolveTextValue = { text ->
                        val angleText = text.removeSuffix("°").trim().toIntOrNull()
                        angleText?.let { angle ->
                            onChangeOffset(offset.copy(angle = angle))
                        }
                    },
                    modifier = Modifier.rightClickable {
                        onChangeOffset(offset.copy(angle = 0))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
