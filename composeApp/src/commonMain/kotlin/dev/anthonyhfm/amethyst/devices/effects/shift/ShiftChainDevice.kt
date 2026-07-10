package dev.anthonyhfm.amethyst.devices.effects.shift

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class ShiftChainDevice : LEDChainDevice<ShiftChainDeviceState>() {
    override val state = MutableStateFlow(ShiftChainDeviceState())
    override val helpRef = "Shift"

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Shift",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(280.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            var beforeState = deviceState.copy()

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Col 1: Hue
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        title = "Hue",
                        text = "${deviceState.hue.toInt()}°",
                        type = DialType.Steps(List(361) { -180 + it }),
                        value = deviceState.hue.toInt(),
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(hue = value.toFloat()) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("°").trim().toIntOrNull()
                                ?.takeIf { it in -180..180 }
                                ?.let { v -> applyResolved { it.copy(hue = v.toFloat()) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(hue = value.toFloat()))
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxHeight(0.8f)) {
                    Separator(orientation = SeparatorOrientation.Vertical)
                }

                // Col 2: Sat Low + Sat High
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        type = DialType.Continuous,
                        title = "Sat Low",
                        text = "${(deviceState.saturationLow * 100).roundToInt()}%",
                        value = deviceState.saturationLow,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(saturationLow = value.coerceIn(0f, 1f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                                ?.let { v -> applyResolved { it.copy(saturationLow = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(saturationLow = value.coerceIn(0f, 1f)))
                        }
                    )
                    Dial(
                        type = DialType.Continuous,
                        title = "Sat High",
                        text = "${(deviceState.saturationMax * 100).roundToInt()}%",
                        value = deviceState.saturationMax,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(saturationMax = value.coerceIn(0f, 1f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                                ?.let { v -> applyResolved { it.copy(saturationMax = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(saturationMax = value.coerceIn(0f, 1f)))
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxHeight(0.8f)) {
                    Separator(orientation = SeparatorOrientation.Vertical)
                }

                // Col 3: Val Low + Val High
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        type = DialType.Continuous,
                        title = "Val Low",
                        text = "${(deviceState.valueLow * 100).roundToInt()}%",
                        value = deviceState.valueLow,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(valueLow = value.coerceIn(0f, 1f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                                ?.let { v -> applyResolved { it.copy(valueLow = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(valueLow = value.coerceIn(0f, 1f)))
                        }
                    )
                    Dial(
                        type = DialType.Continuous,
                        title = "Val High",
                        text = "${(deviceState.valueHigh * 100).roundToInt()}%",
                        value = deviceState.valueHigh,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(valueHigh = value.coerceIn(0f, 1f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                                ?.let { v -> applyResolved { it.copy(valueHigh = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(valueHigh = value.coerceIn(0f, 1f)))
                        }
                    )
                }
            }
        }
    }

    private fun applyResolved(transform: (ShiftChainDeviceState) -> ShiftChainDeviceState) {
        val before = state.value
        val after = transform(before)
        state.value = after
        pushStateChange(before, after)
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val s = state.value
        signalExit?.invoke(n.map { signal ->
            if (signal.color == Color.Transparent || signal.color.alpha == 0f) return@map signal
            signal.copy(color = applyShift(signal.color, s))
        })
    }

    private fun applyShift(color: Color, s: ShiftChainDeviceState): Color {
        val r = color.red;
        val g = color.green;
        val b = color.blue

        val cMax = max(r, max(g, b))
        val cMin = min(r, min(g, b))
        val diff = cMax - cMin

        var h = when {
            diff == 0f -> 0f
            cMax == r -> (60f * ((g - b) / diff) + 360f) % 360f
            cMax == g -> 60f * ((b - r) / diff) + 120f
            else -> 60f * ((r - g) / diff) + 240f
        }
        var sat = if (cMax == 0f) 0f else diff / cMax
        var v = cMax

        h = ((h + s.hue) % 360f + 360f) % 360f
        sat = s.saturationLow + sat * (s.saturationMax - s.saturationLow)
        v = s.valueLow + v * (s.valueHigh - s.valueLow)

        val c = v * sat
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    companion object : ChainDeviceFactory<ShiftChainDeviceState> {
        override val stateClass = ShiftChainDeviceState::class
        override val serializer = ShiftChainDeviceState.serializer()
        override fun create() = ShiftChainDevice()
    }
}

@Serializable
data class ShiftChainDeviceState(
    val hue: Float = 0f,
    val saturationMax: Float = 1f,
    val saturationLow: Float = 0f,
    val valueHigh: Float = 1f,
    val valueLow: Float = 0f
) : DeviceState()
