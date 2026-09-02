package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import com.composeunstyled.Icon
import androidx.compose.runtime.*
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

@Composable
fun PianoRollTransformPanel(
    enabled: Boolean,
    hasMultipleSelection: Boolean,
    onApplyTransform: ((List<MidiNote>) -> List<MidiNote>) -> Unit,
    onGradientSpread: () -> Unit,
    onRandomizeColors: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pinchValue by remember { mutableStateOf(0f) }
    var pinchBilateral by remember { mutableStateOf(false) }
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
                onApplyTransform { PianoRollTransforms.shiftUp(it) }
            }
            Separator()
            LabeledIconButton(Lucide.ArrowDown, description = "Shift Down", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.shiftDown(it) }
            }
            Separator()
            LabeledIconButton(Lucide.ArrowLeft, description = "Shift Left", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.shiftLeft(it) }
            }
            Separator()
            LabeledIconButton(Lucide.ArrowRight, description = "Shift Right", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.shiftRight(it) }
            }
        }

        // Speed
        SectionLabel("Speed")
        ButtonRow {
            LabeledIconButton(Lucide.Rabbit, "×2", "Double Speed", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.doubleSpeed(it) }
            }
            Separator()
            LabeledIconButton(Lucide.Snail, "÷2", "Halve Speed", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.halveSpeed(it) }
            }
        }

        // Note Length
        SectionLabel("Note Length")
        ButtonRow {
            LabeledIconButton(Lucide.Expand, "×2", "Double Length", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.doubleLength(it) }
            }
            Separator()
            LabeledIconButton(Lucide.Minimize2, "÷2", "Halve Length", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.halveLength(it) }
            }
        }

        // Pinch
        SectionLabel("Pinch")
        Row(
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PinchGraph(
                pinch = pinchValue,
                onPinchChange = { pinchValue = it },
                bilateral = pinchBilateral,
                onToggleBilateral = { pinchBilateral = !pinchBilateral },
                modifier = Modifier.size(56.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${(pinchValue * 100f).toInt() / 100f}",
                    style = Theme[typography][small].copy(color = contentColor)
                )
                Button(
                    onClick = {
                        if (pinchValue != 0f) {
                            onApplyTransform { notes -> PianoRollTransforms.pinch(notes, pinchValue, pinchBilateral) }
                            pinchValue = 0f
                        }
                    },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Small,
                    enabled = enabled && pinchValue != 0f
                ) {
                    Text("Apply", style = Theme[typography][small].copy(color = contentColor))
                }
            }
        }

        // Rotate
        SectionLabel("Rotate")
        ButtonRow {
            LabeledIconButton(Lucide.RotateCcw, "−90°", "Rotate Counter-Clockwise", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.rotateCCW(it) }
            }
            Separator()
            LabeledIconButton(Lucide.RefreshCw, "180°", "Rotate 180°", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.rotate180(it) }
            }
            Separator()
            LabeledIconButton(Lucide.RotateCw, "+90°", "Rotate Clockwise", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.rotateCW(it) }
            }
        }

        // Mirror
        SectionLabel("Mirror")
        ButtonRow {
            LabeledIconButton(Lucide.FlipHorizontal2, "Horiz", "Mirror Horizontal", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.mirrorHorizontal(it) }
            }
            Separator()
            LabeledIconButton(Lucide.FlipVertical2, "Vert", "Mirror Vertical", enabled = enabled) {
                onApplyTransform { PianoRollTransforms.mirrorVertical(it) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Theme[colors][border])
        )

        // Gradient Spread
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
