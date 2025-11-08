package dev.anthonyhfm.amethyst.devices.effects.gradient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.effects.gradient.ui.GradientEditorBar
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.ui.components.ColorPicker
import dev.anthonyhfm.amethyst.ui.components.HuePickerBar
import dev.anthonyhfm.amethyst.ui.components.HexColorEditor
import dev.anthonyhfm.amethyst.ui.components.rememberColorPickerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class GradientChainDevice : LEDChainDevice<GradientChainDeviceState>() {
    override val state = MutableStateFlow(GradientChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        val selectedColor: String? = selections.filterIsInstance<Selectable.GradientStep>()
            .find { it.parent == this }
            ?.selectionUUID

        val selectedGradientColor = deviceState.gradientData.find { it.selectionUUID == selectedColor }

        val colorPickerState = key(selectedColor) {
            selectedGradientColor?.let {
                rememberColorPickerState(initialColor = Color(it.r, it.g, it.b))
            }
        }

        LaunchedEffect(selectedGradientColor?.r, selectedGradientColor?.g, selectedGradientColor?.b) {
            val cp = colorPickerState ?: return@LaunchedEffect
            val grad = selectedGradientColor ?: return@LaunchedEffect
            val current = cp.color
            val eps = 0.0005f
            if (kotlin.math.abs(current.red - grad.r) > eps ||
                kotlin.math.abs(current.green - grad.g) > eps ||
                kotlin.math.abs(current.blue - grad.b) > eps) {
                cp.setColor(Color(grad.r, grad.g, grad.b))
            }
        }

        // Track color changes for undo
        var beforeColorGradientSnapshot: List<GradientChainDeviceState.GradientColor>? = null

        LaunchedEffect(colorPickerState?.color) {
            val cp = colorPickerState ?: return@LaunchedEffect
            val grad = selectedGradientColor ?: return@LaunchedEffect
            val newColor = cp.color
            val eps = 0.0005f
            if (kotlin.math.abs(newColor.red - grad.r) < eps &&
                kotlin.math.abs(newColor.green - grad.g) < eps &&
                kotlin.math.abs(newColor.blue - grad.b) < eps) {
                return@LaunchedEffect
            }
            // Nur State aktualisieren ohne Undo während der Interaktion
            state.update { old ->
                val list = old.gradientData.toMutableList()
                val index = list.indexOfFirst { it.selectionUUID == grad.selectionUUID }
                if (index == -1) return@update old
                list[index] = list[index].copy(r = newColor.red, g = newColor.green, b = newColor.blue)
                old.copy(gradientData = list)
            }
        }

        AmethystDevice(
            title = "Gradient",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(
                    width = if (selectedColor != null) {
                        520.dp
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
                        var beforeGradientData: List<GradientChainDeviceState.GradientColor>? = null
                        GradientEditorBar(
                            selectedColor = selectedColor,
                            onSelectionChange = { selectionUUID ->
                                if (selectionUUID != null) {
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
                                // Während Drag kein Undo pushen, nur State aktualisieren
                                state.update { it.copy(gradientData = data) }
                            },
                            onAddGradientPoint = { position ->
                                val before = state.value
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
                                pushStateChange(before, state.value)
                            },
                            onGradientDragStart = {
                                // Merke Zustand vor Drag
                                beforeGradientData = state.value.gradientData.map { it.copy() }
                            },
                            onGradientDragFinish = {
                                val before = beforeGradientData
                                if (before != null) {
                                    val after = state.value.gradientData
                                    // Prüfe Positionsänderungen
                                    val positionsChanged = before.map { it.selectionUUID to it.position } != after.map { it.selectionUUID to it.position }
                                    if (positionsChanged) {
                                        pushStateChange(
                                            before = state.value.copy(gradientData = before),
                                            after = state.value
                                        )
                                    }
                                }
                                beforeGradientData = null
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        var beforeTiming: Pair<Timing, Long> = Pair(deviceState.timing, deviceState.timing.toMsValue(WorkspaceRepository.bpm.value))
                        TimeDial(
                            headline = "Duration",
                            timing = deviceState.timing,
                            onStartValueChange = { t, ms ->
                                beforeTiming = Pair(t, ms)
                            },
                            onSelectTiming = { timing, msValue ->
                                state.update {
                                    it.copy(
                                        timing = timing,
                                        durationMs = msValue.toDouble()
                                    )
                                }
                            },
                            onFinishValueChange = { t, ms ->
                                pushStateChange(
                                    before = state.value.copy(timing = beforeTiming.first, durationMs = beforeTiming.first.toMsValue(WorkspaceRepository.bpm.value).toDouble()),
                                    after = state.value.copy(timing = t, durationMs = ms.toDouble())
                                )
                            }
                        )

                        var beforeGate = deviceState.gate
                        TextDial(
                            headline = "Gate",
                            text = "${(deviceState.gate * 200).toInt()}%",
                            value = deviceState.gate,
                            onStartValueChange = { v ->
                                beforeGate = v
                            },
                            onValueChange = { value ->
                                state.update {
                                    it.copy(gate = value)
                                }
                            },
                            onFinishValueChange = { v ->
                                pushStateChange(
                                    before = state.value.copy(gate = beforeGate),
                                    after = state.value.copy(gate = v)
                                )
                            },
                            onResolveTextValue = {
                                val gateText = it.removeSuffix("%").trim().toIntOrNull()

                                gateText?.let { gate ->
                                    if (gate in 0..200) {
                                        val before = state.value
                                        state.update {
                                            it.copy(gate = gate / 200f)
                                        }
                                        pushStateChange(before, state.value)
                                    }
                                }
                            },
                            modifier = Modifier
                                .rightClickable {
                                    val before = state.value
                                    state.update {
                                        it.copy(gate = 0.5f)
                                    }
                                    pushStateChange(before, state.value)
                                },
                        )
                    }
                }

                if (selectedColor != null && colorPickerState != null) {
                    VerticalDivider()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ColorPicker(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                state = colorPickerState,
                                onSelectionStart = {
                                    beforeColorGradientSnapshot = state.value.gradientData.map { it.copy() }
                                },
                                onSelectionFinish = { c ->
                                    val grad = selectedGradientColor
                                    val beforeSnapshot = beforeColorGradientSnapshot
                                    if (grad != null && beforeSnapshot != null) {
                                        val before = state.value.copy(gradientData = beforeSnapshot)
                                        val afterItem = state.value.gradientData.find { it.selectionUUID == grad.selectionUUID }
                                        val changed = afterItem?.let { it.r != c.red || it.g != c.green || it.b != c.blue } ?: false
                                        if (changed) {
                                            pushStateChange(before, state.value)
                                        }
                                    }
                                    beforeColorGradientSnapshot = null
                                }
                            )

                            HuePickerBar(
                                vertical = true,
                                state = colorPickerState,
                                onSelectionStart = {
                                    beforeColorGradientSnapshot = state.value.gradientData.map { it.copy() }
                                },
                                onSelectionFinish = { c ->
                                    val grad = selectedGradientColor
                                    val beforeSnapshot = beforeColorGradientSnapshot
                                    if (grad != null && beforeSnapshot != null) {
                                        val before = state.value.copy(gradientData = beforeSnapshot)
                                        val afterItem = state.value.gradientData.find { it.selectionUUID == grad.selectionUUID }
                                        val changed = afterItem?.let { it.r != c.red || it.g != c.green || it.b != c.blue } ?: false
                                        if (changed) {
                                            pushStateChange(before, state.value)
                                        }
                                    }
                                    beforeColorGradientSnapshot = null
                                }
                            )
                        }

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

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val bpm = WorkspaceRepository.bpm.value
        val totalDuration = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)
        val gradientSteps = ((GlobalSettings.performanceFPS / GlobalSettings.gradientSmoothness) * (state.value.durationMs * (state.value.gate * 2)).toInt() / 1000).toInt()
        val stepLength = totalDuration / gradientSteps

        val gradient = state.value.gradientData
            .sortedBy { it.position }
            .map { gradientColor ->
                gradientColor.position to Color(gradientColor.r, gradientColor.g, gradientColor.b)
            }

        n.forEach { signal ->
            if (signal.color != Color.Black) {
                val signalOwner = Pair(this, "${signal.x},${signal.y}")

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
                        signalExit?.invoke(
                            listOf(signal.copy(color = color))
                        )
                    }
                }

                Heaven.schedule(
                    delayInMs = totalDuration.toDouble(),
                    owner = signalOwner
                ) {
                    signalExit?.invoke(
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
    val gate: Float = 0.5f,
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