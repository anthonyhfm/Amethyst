package dev.anthonyhfm.amethyst.devices.effects.color

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.ColorPicker
import dev.anthonyhfm.amethyst.ui.components.HexColorEditor
import dev.anthonyhfm.amethyst.ui.components.HuePickerBar
import dev.anthonyhfm.amethyst.ui.components.rememberColorPickerState
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

class ColorChainDevice : LEDChainDevice<ColorChainDeviceState>() {
    override val state = MutableStateFlow(ColorChainDeviceState())

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val deviceState by state.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        val colorPickerState = rememberColorPickerState(
            initialColor = Color(deviceState.r, deviceState.g, deviceState.b)
        )

        LaunchedEffect(deviceState.r, deviceState.g, deviceState.b) {
            val current = colorPickerState.color
            if (!colorsRoughlyEqual(current, deviceState)) {
                colorPickerState.setColor(Color(deviceState.r, deviceState.g, deviceState.b))
            }
        }

        LaunchedEffect(colorPickerState.color) {
            val c = colorPickerState.color
            val current = state.value
            if (!colorsRoughlyEqual(c, current)) {
                state.value = current.copy(r = c.red, g = c.green, b = c.blue)
            }
        }

        ChainDeviceShell(
            title = "Color",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(210.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    var colorBefore by remember { mutableStateOf(Color.White) }

                    LaunchedEffect(Unit) {
                        colorBefore = colorPickerState.color
                    }

                    ColorPicker(
                        modifier = Modifier,
                        state = colorPickerState,
                        onSelectionStart = {
                            colorBefore = colorPickerState.color
                        },
                        onSelectionFinish = {
                            pushStateChange(
                                before = ColorChainDeviceState(colorBefore.red, colorBefore.green, colorBefore.blue),
                                after = ColorChainDeviceState(it.red, it.green, it.blue)
                            )
                        }
                    )

                    HuePickerBar(
                        vertical = true,
                        state = colorPickerState,
                        onSelectionStart = {
                            colorBefore = colorPickerState.color
                        },
                        onSelectionFinish = { _ ->
                            val after = colorPickerState.color
                            pushStateChange(
                                before = ColorChainDeviceState(colorBefore.red, colorBefore.green, colorBefore.blue),
                                after = ColorChainDeviceState(after.red, after.green, after.blue)
                            )
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                ) {
                    HexColorEditor(
                        state = colorPickerState
                    )
                }
            }
        }
    }

    private fun colorsRoughlyEqual(c: Color, s: ColorChainDeviceState, eps: Float = 0.0005f): Boolean =
        kotlin.math.abs(c.red - s.r) < eps &&
                kotlin.math.abs(c.green - s.g) < eps &&
                kotlin.math.abs(c.blue - s.b) < eps


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
