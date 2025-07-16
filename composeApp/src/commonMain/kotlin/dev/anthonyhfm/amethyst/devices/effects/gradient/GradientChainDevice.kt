package dev.anthonyhfm.amethyst.devices.effects.gradient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.ui.GradientEditorBar
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

class GradientChainDevice : ChainDevice<GradientChainDeviceState>() {
    override val state = MutableStateFlow(GradientChainDeviceState())

    @Composable
    override fun Content() {
        val controller = rememberColorPickerController()
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        var selectedColor: Int? by remember { mutableStateOf(null) }

        LaunchedEffect(selectedColor) {
            if (selectedColor != null) {
                controller.selectByColor(
                    color = Color(
                        deviceState.gradientData[selectedColor!!].r,
                        deviceState.gradientData[selectedColor!!].g,
                        deviceState.gradientData[selectedColor!!].b,
                    ),
                    fromUser = false
                )
            }
        }

        AmethystDevice(
            title = "Gradient",
            isSelected = selections.contains(this),
            modifier = Modifier
                .width(
                    width = if (selectedColor != null) {
                        480.dp
                    } else {
                        300.dp
                    }
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .padding(16.dp),

                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    GradientEditorBar(
                        selectedColor = selectedColor,
                        onSelectionChange = {
                            selectedColor = it
                        },
                        colors = deviceState.gradientData,
                        onGradientDataEmit = { data ->
                            state.update {
                                it.copy(
                                    gradientData = data
                                )
                            }
                        },
                        onAddGradientPoint = { position ->
                            val newColor = GradientChainDeviceState.GradientColor(
                                r = 1.0f,
                                g = 1.0f,
                                b = 1.0f,
                                position = position
                            )
                            val updatedColors = deviceState.gradientData.toMutableList().apply {
                                add(newColor)
                            }
                            state.update {
                                it.copy(
                                    gradientData = updatedColors
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TimeDial(
                            headline = "Duration",
                            timing = deviceState.timing,
                            onSelectTiming = { timing, msValue ->
                                state.update {
                                    it.copy(
                                        timing = timing,
                                        durationMs = msValue.toDouble()
                                    )
                                }
                            }
                        )

                        TextDial(
                            headline = "Gate",
                            text = "${(deviceState.gate * 200).toInt()}%",
                            value = deviceState.gate,
                            onValueChange = { value ->
                                state.update {
                                    it.copy(gate = value)
                                }
                            },
                            modifier = Modifier
                                .rightClickable {
                                    state.update {
                                        it.copy(gate = 0.5f) // Reset gate to its original state
                                    }
                                },
                        )
                    }
                }

                if (selectedColor != null) {
                    VerticalDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        HsvColorPicker(
                            controller = controller,
                            onColorChanged = { color ->
                                state.update {
                                    val list = it.gradientData.toMutableList()

                                    list[selectedColor!!] = list[selectedColor!!].copy(
                                        r = color.color.red,
                                        g = color.color.green,
                                        b = color.color.blue
                                    )

                                    return@update it.copy(
                                        gradientData = list
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1 / 1f)
                        )

                        BrightnessSlider(
                            controller = controller,
                            modifier = Modifier
                                .height(24.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    private fun interpolateGradient(gradient: List<Pair<Float, Color>>, progress: Float): Color {
        val (start, end) = gradient.zipWithNext().find { (a, b) -> progress in a.first..b.first }
            ?: return gradient.last().second

        val t = (progress - start.first) / (end.first - start.first)

        return Color(
            red = start.second.red * (1 - t) + end.second.red * t,
            green = start.second.green * (1 - t) + end.second.green * t,
            blue = start.second.blue * (1 - t) + end.second.blue * t
        )
    }

    override fun midiEnter(n: List<Signal>) {
        // jesus christ, this is a lot of math
        val gradientSteps = ((GlobalSettings.perforanceFPS / GlobalSettings.gradientSmoothness) * (state.value.durationMs * (state.value.gate * 2)).toInt() / 1000).toInt()

        val stepLength = (state.value.durationMs * (state.value.gate * 2)) / gradientSteps
        val colors = state.value.gradientData.sortedBy { it.position }.map { it.position to Color(it.r,  it.g, it.b) }

        n.forEach { signal ->
            if (signal.color != Color.Black) {
                for (step in 0..gradientSteps) {
                    val progress = step.toFloat() / gradientSteps
                    val color = interpolateGradient(colors, progress)

                    Heaven.schedule(
                        job = {
                            midiExit?.invoke(
                                listOf(signal.copy(color = color))
                            )
                        },
                        delayInMs = stepLength * step
                    )
                }

                Heaven.schedule(
                    job = {
                        midiExit?.invoke(
                            listOf(signal.copy(color = Color.Black))
                        )
                    },
                    delayInMs = stepLength * (gradientSteps + 1)
                )
            }
        }
    }
}

@Serializable
data class GradientChainDeviceState(
    val gradientData: List<GradientColor> = listOf(
        GradientColor(0f, 1f, 1f, 1f),
        GradientColor(0.5f, 1f, 0f, 0f),
        GradientColor(1f, 0f, 0f, 0f)
    ),
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val durationMs: Double = 0.0,
    val gate: Float = 0.5f, // 100% = 0.5f, 200% = 1.0f
) : DeviceState() {
    @Serializable
    data class GradientColor(
        val position: Float,
        val r: Float,
        val g: Float,
        val b: Float,
    )
}