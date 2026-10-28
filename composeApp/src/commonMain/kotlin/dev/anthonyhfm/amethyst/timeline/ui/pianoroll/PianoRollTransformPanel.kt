package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.composeunstyled.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.platform.LocalWindowInfo
import dev.anthonyhfm.amethyst.ui.components.primitives.Input
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.*
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PinchGraph
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.transforms.PianoRollTransforms
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun PianoRollTransformPanel(
    enabled: Boolean,
    hasMultipleSelection: Boolean,
    onApplyTransform: ((List<MidiNote>) -> List<MidiNote>, Boolean) -> Unit,
    pinchValue: Float,
    onPinchValueChange: (Float) -> Unit,
    pinchBilateral: Boolean,
    onPinchBilateralChange: (Boolean) -> Unit,
    onGradientSpread: () -> Unit,
    onRandomizeColors: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowInfo = LocalWindowInfo.current
    val applyTransform: ((List<MidiNote>) -> List<MidiNote>) -> Unit = { transform ->
        onApplyTransform(transform, windowInfo.keyboardModifiers.isAltPressed)
    }
    var pinchText by remember {
        mutableStateOf(
            value = formatPinchValue(value = pinchValue)
        )
    }

    LaunchedEffect(key1 = pinchValue) {
        if (parsePinchValue(text = pinchText) != pinchValue) {
            pinchText = formatPinchValue(value = pinchValue)
        }
    }
    val contentColor = Theme[colors][foreground]

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Transform",
            style = Theme[typography][small].copy(color = contentColor)
        )

        // Helper components
        SectionLabel("Shift in XY Grid")
        ButtonRow {
            LabeledIconButton(Lucide.ArrowUp, description = "Shift Up", enabled = enabled) {
                applyTransform { PianoRollTransforms.shiftUp(it) }
            }
            Separator()
            LabeledIconButton(Lucide.ArrowDown, description = "Shift Down", enabled = enabled) {
                applyTransform { PianoRollTransforms.shiftDown(it) }
            }
            Separator()
            LabeledIconButton(Lucide.ArrowLeft, description = "Shift Left", enabled = enabled) {
                applyTransform { PianoRollTransforms.shiftLeft(it) }
            }
            Separator()
            LabeledIconButton(Lucide.ArrowRight, description = "Shift Right", enabled = enabled) {
                applyTransform { PianoRollTransforms.shiftRight(it) }
            }
        }

        // Speed
        SectionLabel("Speed")
        ButtonRow {
            LabeledIconButton(Lucide.Rabbit, "×2", "Double Speed", enabled = enabled) {
                applyTransform { PianoRollTransforms.doubleSpeed(it) }
            }
            Separator()
            LabeledIconButton(Lucide.Snail, "÷2", "Halve Speed", enabled = enabled) {
                applyTransform { PianoRollTransforms.halveSpeed(it) }
            }
        }

        SectionLabel("Scramble")
        ButtonRow {
            LabeledIconButton(Lucide.Shuffle, "Pads", "Scramble pads as groups", enabled = enabled) {
                applyTransform { PianoRollTransforms.scrambleByPad(it) }
            }
            Separator()
            LabeledIconButton(Lucide.Dices, "All", "Scramble every note", enabled = enabled) {
                applyTransform { PianoRollTransforms.scrambleAll(it) }
            }
        }

        // Note Length
        SectionLabel("Note Length")
        ButtonRow {
            LabeledIconButton(Lucide.Expand, "×2", "Double Length", enabled = enabled) {
                applyTransform { PianoRollTransforms.doubleLength(it) }
            }
            Separator()
            LabeledIconButton(Lucide.Minimize2, "÷2", "Halve Length", enabled = enabled) {
                applyTransform { PianoRollTransforms.halveLength(it) }
            }
        }

        // Pinch
        SectionLabel(label = "Pinch (−2 to 2)")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(
                    alpha = if (enabled) {
                        1f
                    } else {
                        0.4f
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp)
        ) {
            PinchGraph(
                pinch = pinchValue,
                onPinchChange = { newValue ->
                    val rounded = (newValue * 100f).roundToInt() / 100f

                    onPinchValueChange(rounded)
                },
                bilateral = pinchBilateral,
                onToggleBilateral = {
                    onPinchBilateralChange(!pinchBilateral)
                },
                modifier = Modifier
                    .size(size = 56.dp)
            )

            Input(
                value = pinchText,
                onValueChange = { text ->
                    pinchText = text

                    parsePinchValue(text = text)?.let(onPinchValueChange)
                },
                enabled = enabled,
                modifier = Modifier
                    .weight(weight = 1f)
                    .onFocusChanged { focus ->
                        if (!focus.isFocused) {
                            pinchText = formatPinchValue(value = pinchValue)
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            pinchText = formatPinchValue(value = pinchValue)

                            true
                        } else {
                            false
                        }
                    }
            )

            Button(
                onClick = {
                    if (pinchValue != 0f) {
                        pinchText = formatPinchValue(value = pinchValue)

                        applyTransform { notes ->
                            PianoRollTransforms.pinch(
                                notes = notes,
                                factor = pinchValue,
                                bilateral = pinchBilateral
                            )
                        }
                    }
                },
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Small,
                enabled = enabled && pinchValue != 0f && parsePinchValue(text = pinchText) != null
            ) {
                Text(
                    text = "Apply",
                    style = Theme[typography][small].copy(color = contentColor)
                )
            }
        }

        // Rotate
        SectionLabel("Rotate")
        ButtonRow {
            LabeledIconButton(Lucide.RotateCcw, "−90°", "Rotate Counter-Clockwise", enabled = enabled) {
                applyTransform { PianoRollTransforms.rotateCCW(it) }
            }
            Separator()
            LabeledIconButton(Lucide.RefreshCw, "180°", "Rotate 180°", enabled = enabled) {
                applyTransform { PianoRollTransforms.rotate180(it) }
            }
            Separator()
            LabeledIconButton(Lucide.RotateCw, "+90°", "Rotate Clockwise", enabled = enabled) {
                applyTransform { PianoRollTransforms.rotateCW(it) }
            }
        }

        // Mirror
        SectionLabel("Mirror")
        ButtonRow {
            LabeledIconButton(Lucide.FlipHorizontal2, "Horiz", "Mirror Horizontal", enabled = enabled) {
                applyTransform { PianoRollTransforms.mirrorHorizontal(it) }
            }
            Separator()
            LabeledIconButton(Lucide.FlipVertical2, "Vert", "Mirror Vertical", enabled = enabled) {
                applyTransform { PianoRollTransforms.mirrorVertical(it) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Theme[colors][border])
        )

        Button(
            onClick = onGradientSpread,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
            enabled = hasMultipleSelection
        ) {
            Text("Gradient Spread", style = Theme[typography][small].copy(color = contentColor))
        }

        // Randomize Colors
        Button(
            onClick = onRandomizeColors,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Small,
            enabled = enabled
        ) {
            Text("Randomize Colors", style = Theme[typography][small].copy(color = contentColor))
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = Theme[typography][small].copy(
            color = Theme[colors][mutedForeground],
            fontSize = TextUnit(10f, TextUnitType.Sp)
        )
    )
}

@Composable
private fun RowScope.LabeledIconButton(
    icon: ImageVector,
    label: String? = null,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = Theme[colors][foreground]
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        variant = ButtonVariant.Ghost,
        size = if (label != null) ButtonSize.Small else ButtonSize.Icon,
        enabled = enabled
    ) {
        if (label != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(13.dp), tint = contentColor)
                Text(label, style = Theme[typography][small].copy(color = contentColor))
            }
        } else {
            Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp), tint = contentColor)
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Theme[colors][border]))
}

@Composable
private fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(SmallShape)
            .border(1.dp, Theme[colors][border], SmallShape),
        content = content
    )
}

internal fun formatPinchValue(value: Float): String {
    val rounded = (value * 100f).roundToInt() / 100f

    return rounded.toString()
}

internal fun parsePinchValue(text: String): Float? {
    return text.trim().replace(oldChar = ',', newChar = '.').toFloatOrNull()
        ?.takeIf {
            it.isFinite()
        }
        ?.coerceIn(minimumValue = -2f, maximumValue = 2f)
        ?.let {
            (it * 100f).roundToInt() / 100f
        }
}
