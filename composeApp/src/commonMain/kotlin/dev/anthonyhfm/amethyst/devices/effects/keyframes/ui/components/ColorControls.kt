package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
) {
    val state = rememberColorPickerState()

    LaunchedEffect(color) {
        state.setColor(color)
    }

    LaunchedEffect(state.color) {
        onColorChange(state.color)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorPicker(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
        )

        HuePickerBar(
            state = state,
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