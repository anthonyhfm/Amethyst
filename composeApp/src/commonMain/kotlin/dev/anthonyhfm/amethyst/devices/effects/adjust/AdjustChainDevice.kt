package dev.anthonyhfm.amethyst.devices.effects.adjust

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
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class AdjustChainDevice : LEDChainDevice<AdjustChainDeviceState>() {
    override val state = MutableStateFlow(AdjustChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Adjust",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(180.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            var beforeState = deviceState.copy()

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Col 1: Brightness + Contrast
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    TextDial(
                        headline = "Brightness",
                        text = "${(deviceState.brightness * 100).roundToInt()}%",
                        value = deviceState.brightness / 2f,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(brightness = (value * 2f).coerceIn(0f, 2f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..200 }
                                ?.let { v -> applyResolved { it.copy(brightness = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(brightness = (value * 2f).coerceIn(0f, 2f)))
                        }
                    )
                    TextDial(
                        headline = "Contrast",
                        text = "${(deviceState.contrast * 100).roundToInt()}%",
                        value = deviceState.contrast / 2f,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(contrast = (value * 2f).coerceIn(0f, 2f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..200 }
                                ?.let { v -> applyResolved { it.copy(contrast = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(contrast = (value * 2f).coerceIn(0f, 2f)))
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxHeight(0.8f)) {
                    Separator(orientation = SeparatorOrientation.Vertical)
                }

                // Col 2: Temperature + Tint
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    StepTextDial(
                        headline = "Temp",
                        text = "${(deviceState.temperature * 100).toInt()}",
                        steps = List(201) { -100 + it },
                        value = (deviceState.temperature * 100).toInt(),
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(temperature = value / 100f) } },
                        onResolveTextValue = { text ->
                            text.trim().toIntOrNull()?.takeIf { it in -100..100 }
                                ?.let { v -> applyResolved { it.copy(temperature = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(temperature = value / 100f))
                        }
                    )
                    StepTextDial(
                        headline = "Tint",
                        text = "${(deviceState.tint * 100).toInt()}",
                        steps = List(201) { -100 + it },
                        value = (deviceState.tint * 100).toInt(),
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { value -> state.update { it.copy(tint = value / 100f) } },
                        onResolveTextValue = { text ->
                            text.trim().toIntOrNull()?.takeIf { it in -100..100 }
                                ?.let { v -> applyResolved { it.copy(tint = v / 100f) } }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(tint = value / 100f))
                        }
                    )
                }
            }
        }
    }

    private fun applyResolved(transform: (AdjustChainDeviceState) -> AdjustChainDeviceState) {
        val before = state.value
        val after = transform(before)
        state.value = after
        pushStateChange(before, after)
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val s = state.value
        signalExit?.invoke(n.map { signal ->
            if (signal.color == Color.Transparent || signal.color.alpha == 0f) return@map signal
            signal.copy(color = applyAdjust(signal.color, s))
        })
    }

    private fun applyAdjust(color: Color, s: AdjustChainDeviceState): Color {
        var r = color.red; var g = color.green; var b = color.blue

        // Brightness (multiplicative)
        r = (r * s.brightness).coerceIn(0f, 1f)
        g = (g * s.brightness).coerceIn(0f, 1f)
        b = (b * s.brightness).coerceIn(0f, 1f)

        // Contrast (pivot around 0.5)
        r = ((r - 0.5f) * s.contrast + 0.5f).coerceIn(0f, 1f)
        g = ((g - 0.5f) * s.contrast + 0.5f).coerceIn(0f, 1f)
        b = ((b - 0.5f) * s.contrast + 0.5f).coerceIn(0f, 1f)

        // Temperature: warm (+) raises red / lowers blue; cool (-) vice versa
        r = (r + s.temperature * 0.2f).coerceIn(0f, 1f)
        b = (b - s.temperature * 0.2f).coerceIn(0f, 1f)

        // Tint: positive → green, negative → magenta (red+blue)
        g = (g + s.tint * 0.2f).coerceIn(0f, 1f)
        r = (r - s.tint * 0.1f).coerceIn(0f, 1f)
        b = (b - s.tint * 0.1f).coerceIn(0f, 1f)

        return Color(red = r, green = g, blue = b, alpha = color.alpha)
    }

    companion object : ChainDeviceFactory<AdjustChainDeviceState> {
        override val stateClass = AdjustChainDeviceState::class
        override val serializer = AdjustChainDeviceState.serializer()
        override fun create() = AdjustChainDevice()
    }
}

@Serializable
data class AdjustChainDeviceState(
    val brightness: Float = 1f,
    val contrast: Float = 1f,
    val temperature: Float = 0f,
    val tint: Float = 0f
) : DeviceState()
