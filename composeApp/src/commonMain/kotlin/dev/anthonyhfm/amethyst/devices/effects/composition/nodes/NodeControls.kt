package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
internal fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Theme[typography][small], color = Theme[colors][mutedForeground])
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Composable
internal fun ColorButton(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Button(
        onClick = {
            val next = when {
                value < 0.34f -> 0.5f
                value < 0.67f -> 1f
                else -> 0f
            }
            onChange(next)
        },
        variant = ButtonVariant.Outline,
        size = ButtonSize.Small,
    ) {
        Text(
            text = "$label ${(value * 100).toInt()}",
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )
    }
}

@Composable
internal fun ColorButtons(
    red: Float,
    green: Float,
    blue: Float,
    onRed: (Float) -> Unit,
    onGreen: (Float) -> Unit,
    onBlue: (Float) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorButton("R", red, onRed)
        ColorButton("G", green, onGreen)
        ColorButton("B", blue, onBlue)
    }
}

internal fun formatOneDecimal(value: Float): String =
    ((value * 10f).toInt() / 10f).toString()
