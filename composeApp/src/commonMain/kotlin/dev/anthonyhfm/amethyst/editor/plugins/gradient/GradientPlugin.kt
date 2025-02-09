package dev.anthonyhfm.amethyst.editor.plugins.gradient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.components.TextDial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class GradientPlugin : EffectPlugin() {
    override var isEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    private val gradientData: MutableStateFlow<List<GradientColor>> = MutableStateFlow(
        value = listOf(
            GradientColor(0f, Color.White),
            GradientColor(0.5f, Color.Red),
            GradientColor(1f, Color.Black)
        )
    )

    private val gradientSteps: MutableStateFlow<Int> = MutableStateFlow(20)
    private val gradientDuration: MutableStateFlow<Int> = MutableStateFlow(300) // Duration in MS

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val colors by gradientData.collectAsState()

        AmethystPlugin(
            title = "Gradient",
            enabled = isEnabled.collectAsState().value,
            onChangeEnabled = {
                scope.launch {
                    isEnabled.emit(it)
                }
            },
            modifier = Modifier
                .width(300.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),

                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Box(
                    modifier = Modifier
                ) {
                    Canvas(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .fillMaxWidth()
                            .height(28.dp)
                    ) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = colors.sortedBy { it.position }
                                    .map { it.position to it.color }
                                    .toTypedArray(), // Hier setzen wir die Farben exakt an ihre Positionen
                                startX = 0f,
                                endX = size.width
                            ),
                            size = size
                        )
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .fillMaxWidth()
                    ) {
                        colors.forEachIndexed { index, color ->
                            var pos: Float by remember { mutableStateOf(color.position) }

                            LaunchedEffect(pos) {
                                gradientData.emit(
                                    value = colors.mapIndexed { i, it ->
                                        if (i == index) it.copy(position = pos) else it
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .offset(x = -6.dp, y = -5.dp)
                                    .offset(
                                        x = maxWidth * pos
                                    )
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .height(38.dp)
                                    .width(12.dp)
                                    .background(color.color)
                                    .border(2.dp, Color.White, CircleShape)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDrag = { input, offset ->
                                                input.consume()

                                                val pct = (offset.x / density.density).dp / maxWidth
                                                val newPos = (pos + pct).coerceIn(0f, 1f)

                                                pos = newPos
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),

                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    var duration: Float by remember { mutableStateOf(0f) }
                    var steps: Float by remember { mutableStateOf(0f) }

                    TextDial(
                        text = "Duration",
                        value = duration,
                        onValueChange = {
                            duration = it
                        }
                    )

                    TextDial(
                        text = "Steps",
                        value = steps,
                        onValueChange = {
                            steps = it
                        }
                    )
                }
            }
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        if (data.r != 0 || data.g != 0 || data.b != 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val stepLength = gradientDuration.value / gradientSteps.value
                val colors = gradientData.value.toList().sortedBy { it.position }.map { it.position to it.color }

                for (step in 0..gradientSteps.value) {
                    val progress = step.toFloat() / gradientSteps.value
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

    data class GradientColor(
        var position: Float,
        var color: Color,
    )
}
