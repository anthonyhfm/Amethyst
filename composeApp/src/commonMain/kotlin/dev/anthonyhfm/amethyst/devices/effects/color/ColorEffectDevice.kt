package dev.anthonyhfm.amethyst.devices.effects.color

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin

class ColorEffectDevice : EffectDevice() {
    var color: Color = Color.White

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()

        val controller = rememberColorPickerController()

        LaunchedEffect(Unit) {
            controller.selectByColor(color, false)
        }

        AmethystPlugin(
            title = "Color",
            modifier = Modifier
                .width(200.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HsvColorPicker(
                    controller = controller,
                    onColorChanged = {
                        color = it.color
                    },
                    modifier = Modifier
                        .size(170.dp)
                )

                Spacer(Modifier.weight(1f))

                BrightnessSlider(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        if (data.r != 0 || data.g != 0 || data.b != 0) {
            midiOutput(
                data.copy(
                    r = (63 * color.red).toInt(),
                    g = (63 * color.green).toInt(),
                    b = (63 * color.blue).toInt()
                )
            )
        } else {
            midiOutput(data)
        }
    }
}