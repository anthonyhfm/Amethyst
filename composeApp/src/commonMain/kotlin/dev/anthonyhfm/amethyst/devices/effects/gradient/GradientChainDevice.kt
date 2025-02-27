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
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.ui.GradientEditorBar
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class GradientChainDevice : ChainDevice<GradientChainDeviceState>() {
    override val state = MutableStateFlow(GradientChainDeviceState())

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val controller = rememberColorPickerController()

        val colors = state.collectAsState().value.gradientData
        var selectedColor: Int? by remember { mutableStateOf(null) }
        val duration = state.collectAsState().value.durationMs
        val steps = state.collectAsState().value.steps

        LaunchedEffect(selectedColor) {
            if (selectedColor != null) {
                controller.selectByColor(
                    color = Color(
                        colors[selectedColor!!].r,
                        colors[selectedColor!!].g,
                        colors[selectedColor!!].b,
                    ),
                    fromUser = false
                )
            }
        }

        AmethystDevice(
            title = "Gradient",
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
                        colors = colors,
                        onGradientDataEmit = { data ->
                            state.update {
                                it.copy(
                                    gradientData = data
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextDial(
                            headline = "Duration",
                            text = "${duration.toInt()}ms",
                            value = duration.toFloat() / 1000f,
                            onValueChange = { duration ->
                                state.update {
                                    it.copy(
                                        durationMs = (duration * 1000).toDouble()
                                    )
                                }
                            }
                        )

                        TextDial(
                            headline = "Steps",
                            text = "$steps",
                            value = steps / 100f,
                            onValueChange = { steps ->
                                state.update {
                                    it.copy(
                                        steps = (steps * 100).toInt()
                                    )
                                }
                            }
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
        val stepLength = state.value.durationMs / state.value.steps
        val colors = state.value.gradientData.sortedBy { it.position }.map { it.position to Color(it.r,  it.g, it.b) }

        n.forEach { signal ->
            if (signal.color != Color.Black) {
                for (step in 0..state.value.steps) {
                    val progress = step.toFloat() / state.value.steps
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
            }
        }

        /*
        val stepLength = state.value.durationMs / state.value.steps
        val colors = state.value.gradientData.sortedBy { it.position }.map { it.position to Color(it.r,  it.g, it.b) }

        for (step in 0..state.value.steps) {
            val progress = step.toFloat() / state.value.steps
            val color = interpolateGradient(colors, progress)

            val midiData = data.copy(
                r = (color.red * 63).toInt().coerceIn(0, 63),
                g = (color.green * 63).toInt().coerceIn(0, 63),
                b = (color.blue * 63).toInt().coerceIn(0, 63)
            )

            midiOutput(midiData)

            delay(stepLength.milliseconds)
        }
        midiOutput(data.copy(r = 0, g = 0, b = 0))
         */
    }
}

@Serializable
data class GradientChainDeviceState(
    val gradientData: List<GradientColor> = listOf(
        GradientColor(0f, 1f, 1f, 1f),
        GradientColor(0.5f, 1f, 0f, 0f),
        GradientColor(1f, 0f, 0f, 0f)
    ),
    val steps: Int = 20,
    val durationMs: Double = 300.0
) : DeviceState() {
    @Serializable
    data class GradientColor(
        val position: Float,
        val r: Float,
        val g: Float,
        val b: Float,
    )
}