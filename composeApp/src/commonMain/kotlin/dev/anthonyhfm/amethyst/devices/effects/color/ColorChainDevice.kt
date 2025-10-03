package dev.anthonyhfm.amethyst.devices.effects.color

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.ColorPicker
import dev.anthonyhfm.amethyst.ui.components.HexColorEditor
import dev.anthonyhfm.amethyst.ui.components.HuePickerBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class ColorChainDevice : LEDChainDevice<ColorChainDeviceState>() {
    override val state = MutableStateFlow(ColorChainDeviceState())

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
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
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(220.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),

                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ColorPicker(
                        modifier = Modifier
                            .weight(1f)
                    )

                    HuePickerBar(
                        vertical = true
                    )
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                ) {
                    HexColorEditor(
                        hex = "#ffffff",
                        onEditHex = {

                        }
                    )
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        signalExit?.invoke(
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