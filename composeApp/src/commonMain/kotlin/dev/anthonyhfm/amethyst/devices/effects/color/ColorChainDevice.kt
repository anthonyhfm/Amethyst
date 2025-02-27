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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class ColorChainDevice : ChainDevice<ColorChainDeviceState>() {
    override val state = MutableStateFlow(ColorChainDeviceState())

    @Composable
    override fun Content() {
        val controller = rememberColorPickerController()

        LaunchedEffect(Unit) {
            controller.selectByColor(
                color = Color(
                    red = state.value.r,
                    green = state.value.g,
                    blue = state.value.b
                ),
                fromUser = false
            )
        }

        AmethystDevice(
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
                    onColorChanged = { color ->
                        state.update {
                            it.copy(
                                r = color.color.red,
                                g = color.color.green,
                                b = color.color.blue
                            )
                        }
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

    override fun midiEnter(n: List<Signal>) {
        midiExit?.invoke(
            n.map {
                if (it.color != Color.Black) {
                    it.copy(
                        color = Color(state.value.r, state.value.g, state.value.b)
                    )
                } else {
                    it
                }
            }
        )
    }
}

@Serializable
data class ColorChainDeviceState(
    val r: Float = 1f,
    val g: Float = 1f,
    val b: Float = 1f
) : DeviceState()