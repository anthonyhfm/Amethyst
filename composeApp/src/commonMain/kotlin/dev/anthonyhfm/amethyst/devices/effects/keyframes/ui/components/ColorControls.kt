package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.ColorPicker
import dev.anthonyhfm.amethyst.ui.components.HexColorEditor
import dev.anthonyhfm.amethyst.ui.components.HuePickerBar
import dev.anthonyhfm.amethyst.ui.components.rememberColorPickerState

@Composable
fun ColorControls(
    color: Color,
    onColorChange: (Color) -> Unit,
    onInteractionStart: () -> Unit = {},
    onInteractionFinish: () -> Unit = {},
) {
    val state = rememberColorPickerState(color)
    var lastExternalColor by remember { mutableStateOf(color) }

    LaunchedEffect(color) {
        if (color != lastExternalColor) {
            lastExternalColor = color
            if (state.color != color) state.setColor(color)
        }
    }

    LaunchedEffect(state.color) {
        if (state.color != lastExternalColor) {
            onColorChange(state.color)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorPicker(
            state = state,
            onSelectionStart = onInteractionStart,
            onSelectionFinish = { onInteractionFinish() },
            modifier = Modifier
                .fillMaxWidth()
        )

        HuePickerBar(
            state = state,
            onSelectionStart = onInteractionStart,
            onSelectionFinish = { onInteractionFinish() },
            modifier = Modifier
                .fillMaxWidth()
        )

        HexColorEditor(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
        )
    }
}
