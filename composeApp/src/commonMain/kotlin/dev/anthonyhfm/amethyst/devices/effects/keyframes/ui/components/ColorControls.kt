package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun ColorControls(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val controller = rememberColorPickerController()

    LaunchedEffect(color) {
        controller.selectByColor(color, fromUser = false)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HsvColorPicker(
            controller = controller,
            onColorChanged = { color ->
                onColorChange(color.color)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        BrightnessSlider(
            controller = controller,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )
    }
}