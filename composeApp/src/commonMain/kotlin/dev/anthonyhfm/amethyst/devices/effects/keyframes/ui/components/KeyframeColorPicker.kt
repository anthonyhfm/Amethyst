package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun KeyframeColorPicker(
    color: Color,
    onColorChanged: (Color) -> Unit
) {
    val controller = rememberColorPickerController()

    LaunchedEffect(color) {
        controller.selectByColor(color, false)
    }

    Column(
        modifier = Modifier
            .padding(24.dp),

        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HsvColorPicker(
            controller = controller,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1 / 1f),
            onColorChanged = {
                onColorChanged(it.color)
            }
        )

        BrightnessSlider(
            controller = controller,
            modifier = Modifier
                .height(24.dp)
                .fillMaxWidth()
        )
    }
}