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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.gradient.ui.GradientEditorBar
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class GradientChainDevice : ChainDevice<GradientChainDeviceState>() {
    override val state = MutableStateFlow(GradientChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        val selectedColor: String? = selections.filterIsInstance<Selectable.GradientStep>()
            .find { it.parent == this }
            ?.selectionUUID

        // Create a new controller for each selected color to avoid state pollution
        // Use selectedColor as key to force recreation when selection changes
        val controller = key(selectedColor) {
            rememberColorPickerController()
        }

        // Reset and initialize controller when selection changes
        LaunchedEffect(selectedColor) {
            if (selectedColor != null) {
                val color = deviceState.gradientData.find { it.selectionUUID == selectedColor }
                if (color != null) {
                    // Force a complete reset of the controller
                    controller.selectByColor(Color(color.r, color.g, color.b), false)
                }
            }
        }

        AmethystDevice(
            title = "Gradient",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
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
                    key(deviceState.gradientData.size) {
                        GradientEditorBar(
                            selectedColor = selectedColor,
                            onSelectionChange = { selectionUUID ->
                                if (selectionUUID != null) {
                                    // Find the gradient color by selectionUUID to ensure we have the correct one
                                    val gradientColor = deviceState.gradientData.find { it.selectionUUID == selectionUUID }
                                    if (gradientColor != null) {
                                        val index = deviceState.gradientData.indexOf(gradientColor)
                                        SelectionManager.select(Selectable.GradientStep(this@GradientChainDevice, index), single = true)
                                    }
                                } else {
                                    SelectionManager.clear()
                                }
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
                    }

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
                            onResolveTextValue = {
                                val gateText = it.removeSuffix("%").trim().toIntOrNull()

                                gateText?.let { gate ->
                                    if (gate in 0..200) {
                                        state.update {
                                            it.copy(gate = gate / 200f) // Convert to float between 0.0 and 1.0
                                        }
                                    }
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
                        key(selectedColor) {
                            HsvColorPicker(
                                controller = controller,
                                onColorChanged = { color ->
                                    if (color.fromUser) {
                                        // Capture current selectedColor to avoid closure issues
                                        val currentSelectedColor = selectedColor
                                        state.update {
                                            val list = it.gradientData.toMutableList()
                                            val index = list.indexOfFirst { it.selectionUUID == currentSelectedColor }

                                            if (index == -1) return@update it

                                            list[index] = list[index].copy(
                                                r = color.color.red,
                                                g = color.color.green,
                                                b = color.color.blue
                                            )

                                            return@update it.copy(
                                                gradientData = list
                                            )
                                        }
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
        val bpm = WorkspaceRepository.bpm.value
        val totalDuration = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)
        val gradientSteps = ((GlobalSettings.perforanceFPS / GlobalSettings.gradientSmoothness) * (state.value.durationMs * (state.value.gate * 2)).toInt() / 1000).toInt()
        val stepLength = totalDuration / gradientSteps

        val gradient = state.value.gradientData
            .sortedBy { it.position }
            .map { gradientColor ->
                gradientColor.position to Color(gradientColor.r, gradientColor.g, gradientColor.b)
            }

        n.forEach { signal ->
            if (signal.color != Color.Black) {
                // Create unique owner for this specific signal/button combination
                val signalOwner = Pair(this, "${signal.x},${signal.y}")

                // Cancel nur die Jobs für diesen spezifischen Button
                Heaven.cancelJobs { job ->
                    job.owner is Pair<*, *> &&
                    job.owner.first == this &&
                    job.owner.second == "${signal.x},${signal.y}"
                }

                for (step in 0..gradientSteps) {
                    val progress = step.toFloat() / gradientSteps
                    val color = interpolateGradient(gradient, progress)

                    Heaven.schedule(
                        delayInMs = (stepLength * step).toDouble(),
                        owner = signalOwner
                    ) {
                        midiExit?.invoke(
                            listOf(signal.copy(color = color))
                        )
                    }
                }

                Heaven.schedule(
                    delayInMs = totalDuration.toDouble(),
                    owner = signalOwner
                ) {
                    midiExit?.invoke(
                        listOf(signal.copy(color = Color.Black))
                    )
                }
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

        override val selectionUUID: String = UUID.randomUUID(),
    ) : Selectable
}