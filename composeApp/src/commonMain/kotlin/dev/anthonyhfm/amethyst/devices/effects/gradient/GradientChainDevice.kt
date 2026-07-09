package dev.anthonyhfm.amethyst.devices.effects.gradient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.settings.data.GeneralSettings
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.getDeviceCapabilities
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.effects.gradient.ui.GradientEditorBar
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.ui.components.ColorPicker
import dev.anthonyhfm.amethyst.ui.components.HuePickerBar
import dev.anthonyhfm.amethyst.ui.components.HexColorEditor
import dev.anthonyhfm.amethyst.ui.components.rememberColorPickerState
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TimeDial
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

@Serializable
enum class GradientSmoothness {
    Linear,
    Smooth,
    Sharp,
    Fast,
    Slow,
    Hold,
    Release
}

class GradientChainDevice : LEDChainDevice<GradientChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(GradientChainDeviceState())
    override val helpRef = "Gradient"

    private data class FadeSignature(
        val gradientHash: Int,
        val fps: Int,
        val totalTimeBits: Long,
        val gradientSteps: Int?
    )

    private data class GradientRunKey(
        val origin: Any?,
        val x: Int,
        val y: Int,
        val layer: Int,
        val blendingMode: Signal.LED.BlendingMode
    )

    private var cachedFadeSignature: FadeSignature? = null
    private var cachedFade: List<FadeInfo> = emptyList()
    private val colorStepBuffer = mutableListOf<Color>()
    private val stepCountBuffer = mutableListOf<Int>()
    private val cutoffBuffer = mutableListOf<Int>()
    private val activeRunTokens = mutableMapOf<GradientRunKey, Long>()
    private var nextRunToken = 0L

    private fun selectGradientColor(selectionUUID: String?) {
        if (selectionUUID == null) {
            SelectionManager.clear()
            return
        }

        val index = state.value.gradientData.indexOfFirst { it.selectionUUID == selectionUUID }
        if (index != -1) {
            SelectionManager.select(Selectable.GradientStep(this, index), single = true)
        }
    }

    private fun addGradientPoint(position: Float): String? {
        val before = state.value
        val newColor = GradientChainDeviceState.GradientColor(
            r = 1.0f,
            g = 1.0f,
            b = 1.0f,
            position = position,
            rawPosition = position
        )

        state.update { old ->
            val steps = old.gradientSteps
            if (steps != null && old.gradientData.size >= steps) {
                old
            } else {
                old.copy(
                    gradientData = snapColorsNonOverlapping(old.gradientData + newColor, steps)
                )
            }
        }

        val after = state.value
        if (before != after) {
            pushStateChange(before, after)
            return newColor.selectionUUID
        }

        return null
    }

    private fun updateGradientPointPosition(selectionUUID: String, position: Float, rawPosition: Float) {
        state.update { old ->
            val updatedColors = old.gradientData.map { color ->
                if (color.selectionUUID == selectionUUID) {
                    color.copy(
                        position = position.coerceIn(0f, 1f),
                        rawPosition = rawPosition.coerceIn(0f, 1f)
                    )
                } else {
                    color
                }
            }

            old.copy(gradientData = snapColorsNonOverlapping(updatedColors, old.gradientSteps))
        }
    }

    private fun updateGradientPointSmoothness(selectionUUID: String, smoothness: GradientSmoothness) {
        val before = state.value
        state.update { old ->
            val updatedColors = old.gradientData.map { color ->
                if (color.selectionUUID == selectionUUID) {
                    color.copy(smoothness = smoothness)
                } else {
                    color
                }
            }
            old.copy(gradientData = updatedColors)
        }
        pushStateChange(before, state.value)
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }
        val selectedColor: String? = selections.filterIsInstance<Selectable.GradientStep>()
            .find { it.parent == this }
            ?.selectionUUID
        val selectedGradientColor = deviceState.gradientData.find { it.selectionUUID == selectedColor }
        val colorPickerState = key(selectedColor) {
            selectedGradientColor?.let {
                rememberColorPickerState(initialColor = Color(it.r, it.g, it.b))
            }
        }
        val beforeColorGradientSnapshot = remember(selectedColor) {
            mutableStateOf<List<GradientChainDeviceState.GradientColor>?>(null)
        }
        val beforeGradientState = remember(deviceState.gradientData.map { it.selectionUUID }) {
            mutableStateOf<GradientChainDeviceState?>(null)
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

        ChainDeviceShell(
            title = "Gradient",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier
                .width(
                    width = if (selectedColor != null && selectedGradientColor != null) {
                        520.dp
                    } else {
                        300.dp
                    }
                ),
            titleBarModifier = LocalTitleBarModifier.current,
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
                ) {
                    Spacer(Modifier.height(16.dp))

                    key(deviceState.gradientData.size) {
                        GradientEditorBar(
                            selectedColor = selectedColor,
                            onSelectionChange = { selectionUUID ->
                                selectGradientColor(selectionUUID)
                            },
                            colors = deviceState.gradientData,
                            onGradientPointMoved = { selectionUUID, position, rawPosition ->
                                updateGradientPointPosition(selectionUUID, position, rawPosition)
                            },
                            onAddGradientPoint = { position ->
                                addGradientPoint(position)?.let { newSelectionUUID ->
                                    selectGradientColor(newSelectionUUID)
                                }
                            },
                            onGradientDragStart = {
                                beforeGradientState.value = state.value
                            },
                            onGradientDragFinish = {
                                val before = beforeGradientState.value
                                if (before != null) {
                                    val after = state.value
                                    if (before.gradientData != after.gradientData) {
                                        pushStateChange(before = before, after = after)
                                    }
                                }
                                beforeGradientState.value = null
                            },
                            onSmoothnessChange = { selectionUUID, smoothness ->
                                updateGradientPointSmoothness(selectionUUID, smoothness)
                            },
                            gradientSteps = deviceState.gradientSteps,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

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
                            text = "${(deviceState.gate * 200).roundToInt()}%",
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
                        val gradientStepsList = (deviceState.gradientData.size.coerceAtLeast(2)..16).toList() + null
                        val stepsDisplayText = when (deviceState.gradientSteps) {
                            null -> "INF"
                            in 2..16 -> deviceState.gradientSteps.toString()
                            else -> "INF"
                         }
                        var beforeState = state.value
                        StepTextDial(
                            headline = "Steps",
                            value = deviceState.gradientSteps,
                            steps = gradientStepsList,
                            text = stepsDisplayText,
                            onResolveTextValue = { text ->
                                 val parsed = text.trim().toIntOrNull()
                                 val minSteps = deviceState.gradientData.size.coerceAtLeast(2)
                                 if (parsed != null) {
                                     val finalStepValue = parsed.coerceAtLeast(minSteps).coerceAtMost(16)
                                     val before = state.value
                                     state.update { old ->
                                         val snappedColors = snapColorsNonOverlapping(old.gradientData, finalStepValue)
                                         old.copy(gradientSteps = finalStepValue, gradientData = snappedColors)
                                     }
                                     pushStateChange(before, state.value)
                                 } else if (text.trim().equals("inf", ignoreCase = true)) {
                                     val before = state.value
                                     state.update { old ->
                                         val unsnappedColors = snapColorsNonOverlapping(old.gradientData, null)
                                         old.copy(gradientSteps = null, gradientData = unsnappedColors)
                                     }
                                     pushStateChange(before, state.value)
                                  }
                              },
                            onStartValueChange = {
                                beforeState = state.value
                             },
                            onFinishValueChange = {
                                pushStateChange(
                                    before = beforeState,
                                    after = state.value
                                 )
                             },
                            onValueChange = { stepValue ->
                               state.update { old ->
                                    val minSteps = old.gradientData.size.coerceAtLeast(2)
                                    val finalStepValue = if (stepValue != null) stepValue.coerceAtLeast(minSteps) else null
                                    val snappedColors = snapColorsNonOverlapping(old.gradientData, finalStepValue)
                                    old.copy(gradientSteps = finalStepValue, gradientData = snappedColors)
                                }
                             },
                            modifier = Modifier
                                 .rightClickable {
                                    val before = state.value
                                    state.update { old ->
                                        val unsnappedColors = snapColorsNonOverlapping(old.gradientData, null)
                                        old.copy(gradientSteps = null, gradientData = unsnappedColors)
                                    }
                                    pushStateChange(before, state.value)
                                 },
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp)
                    ) {
                        Checkbox(
                            checked = deviceState.loop,
                            onCheckedChange = { checked ->
                                val before = state.value
                                state.update { it.copy(loop = checked) }
                                pushStateChange(before, state.value)
                            },
                        )

                        Text(
                            text = "Loop",
                            style = Theme[typography][small],
                            color = Theme[colors][foreground],
                        )
                    }
                }

                if (selectedColor != null && colorPickerState != null && selectedGradientColor != null) {
                    Separator(orientation = SeparatorOrientation.Vertical)

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
                                    beforeColorGradientSnapshot.value = state.value.gradientData.map { it.copy() }
                                },
                                onSelectionFinish = { c ->
                                    val grad = selectedGradientColor
                                    val beforeSnapshot = beforeColorGradientSnapshot.value
                                    if (grad != null && beforeSnapshot != null) {
                                        val before = state.value.copy(gradientData = beforeSnapshot)
                                        val afterItem = state.value.gradientData.find { it.selectionUUID == grad.selectionUUID }
                                        val changed = afterItem?.let { it.r != c.red || it.g != c.green || it.b != c.blue } ?: false
                                        if (changed) {
                                            pushStateChange(before, state.value)
                                        }
                                    }
                                    beforeColorGradientSnapshot.value = null
                                }
                            )

                            HuePickerBar(
                                vertical = true,
                                state = colorPickerState,
                                onSelectionStart = {
                                    beforeColorGradientSnapshot.value = state.value.gradientData.map { it.copy() }
                                },
                                onSelectionFinish = { c ->
                                    val grad = selectedGradientColor
                                    val beforeSnapshot = beforeColorGradientSnapshot.value
                                    if (grad != null && beforeSnapshot != null) {
                                        val before = state.value.copy(gradientData = beforeSnapshot)
                                        val afterItem = state.value.gradientData.find { it.selectionUUID == grad.selectionUUID }
                                        val changed = afterItem?.let { it.r != c.red || it.g != c.green || it.b != c.blue } ?: false
                                        if (changed) {
                                            pushStateChange(before, state.value)
                                        }
                                    }
                                    beforeColorGradientSnapshot.value = null
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

    data class FadeInfo(
        val color: Color,
        val time: Double,
        val isHold: Boolean = false
    )

    private fun resolvedSamplingFps(baseFps: Int = GeneralSettings.performanceFPS.value): Int {
        val qualityScale = GeneralSettings.gradientSmoothness.value.coerceIn(0.5f, 1f)
        val lowRamScale = if (getDeviceCapabilities().lowRamUsageMode) 0.75f else 1f
        val scaledFps = (baseFps * qualityScale * lowRamScale).roundToInt()
        return scaledFps.coerceIn(12, baseFps.coerceAtLeast(12))
    }

    private fun runKey(signal: Signal.LED) = GradientRunKey(
        origin = signal.origin,
        x = signal.x,
        y = signal.y,
        layer = signal.layer,
        blendingMode = signal.blendingMode
    )

    private fun startRun(ownerKey: GradientRunKey): Long {
        val runToken = ++nextRunToken
        activeRunTokens[ownerKey] = runToken
        return runToken
    }

    private fun isRunActive(ownerKey: GradientRunKey, runToken: Long): Boolean {
        return activeRunTokens[ownerKey] == runToken
    }

    private fun clearRun(ownerKey: GradientRunKey, runToken: Long? = null) {
        if (runToken == null || activeRunTokens[ownerKey] == runToken) {
            activeRunTokens.remove(ownerKey)
        }
    }

    private fun cancelRun(ownerKey: GradientRunKey) {
        clearRun(ownerKey)
        Heaven.cancelJobsForOwner(this, ownerKey)
    }

    private fun easeTime(type: GradientSmoothness, start: Double, end: Double, value: Double): Double {
        if (type == GradientSmoothness.Linear) return value
        if (type == GradientSmoothness.Hold) return if (start != value) end - 0.1 else start
        if (type == GradientSmoothness.Release) return start

        val duration = end - start
        val proportion = (value - start) / duration

        val easedProportion = when (type) {
            GradientSmoothness.Fast -> proportion.pow(2)
            GradientSmoothness.Slow -> 1.0 - (1.0 - proportion).pow(2)
            GradientSmoothness.Sharp -> {
                if (proportion < 0.5) {
                    (proportion - 0.5).pow(2) * -2 + 0.5
                } else {
                    (proportion - 0.5).pow(2) * 2 + 0.5
                }
            }
            GradientSmoothness.Smooth -> {
                if (proportion < 0.5) {
                    proportion.pow(2) * 2
                } else {
                    (proportion - 1).pow(2) * -2 + 1
                }
            }
            else -> proportion
        }

        return start + duration * easedProportion
    }

    private fun snapColorsNonOverlapping(
        colors: List<GradientChainDeviceState.GradientColor>,
        steps: Int?
    ): List<GradientChainDeviceState.GradientColor> {
        if (colors.isEmpty()) return colors
        if (steps == null || steps !in 2..16) {
            // INF mode: restore to rawPosition if available, otherwise keep position
            return colors.map { c ->
                c.copy(position = c.rawPosition ?: c.position, rawPosition = c.rawPosition ?: c.position)
            }.sortedBy { it.position }
        }

        val sorted = colors.map { c ->
            c.copy(rawPosition = c.rawPosition ?: c.position)
        }.sortedBy { it.rawPosition }

        val P = sorted.size
        val S = steps

        // Initialize target step indices
        val s = IntArray(P) { i ->
            ((sorted[i].rawPosition ?: sorted[i].position) * (S - 1)).roundToInt().coerceIn(0, S - 1)
        }

        // Pass 1: Left-to-Right
        for (i in 1 until P) {
            if (s[i] < s[i - 1] + 1) {
                s[i] = s[i - 1] + 1
            }
        }

        // Pass 2: Right-to-Left
        if (s[P - 1] > S - 1) {
            s[P - 1] = S - 1
        }
        for (i in P - 2 downTo 0) {
            if (s[i] > s[i + 1] - 1) {
                s[i] = s[i + 1] - 1
            }
        }

        // Map back to colors
        return sorted.mapIndexed { i, c ->
            val snappedPos = s[i].toFloat() / (S - 1)
            c.copy(position = snappedPos)
        }
    }

    private fun interpolateColor(
        gradientData: List<GradientChainDeviceState.GradientColor>,
        progress: Float
    ): Color {
        if (gradientData.isEmpty()) return Color.Black
        val clampedP = progress.coerceIn(0f, 1f)
        if (gradientData.size == 1) {
            val first = gradientData[0]
            return Color(first.r, first.g, first.b)
        }

        var segmentIndex = 0
        for (i in 0 until gradientData.size - 1) {
            if (clampedP >= gradientData[i].position && clampedP <= gradientData[i + 1].position) {
                segmentIndex = i
                break
            }
        }

        val startColor = gradientData[segmentIndex]
        val endColor = gradientData.getOrNull(segmentIndex + 1) ?: gradientData.last()
        val smoothness = startColor.smoothness

        val segmentStart = startColor.position.toDouble()
        val segmentEnd = endColor.position.toDouble()
        val segmentDuration = segmentEnd - segmentStart

        val linearT = if (segmentDuration > 0.0001) {
            ((clampedP - segmentStart) / segmentDuration).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val easedT = when (smoothness) {
            GradientSmoothness.Linear -> linearT
            GradientSmoothness.Hold -> {
                if (linearT < 0.95) 0.0 else 1.0
            }
            GradientSmoothness.Release -> {
                if (linearT > 0.05) 1.0 else 0.0
            }
            GradientSmoothness.Fast -> {
                kotlin.math.sqrt(linearT)
            }
            GradientSmoothness.Slow -> {
                1.0 - kotlin.math.sqrt(1.0 - linearT)
            }
            GradientSmoothness.Sharp -> {
                if (linearT < 0.5) {
                    0.5 - kotlin.math.sqrt(0.5 - linearT) / kotlin.math.sqrt(2.0)
                } else {
                    0.5 + kotlin.math.sqrt(linearT - 0.5) / kotlin.math.sqrt(2.0)
                }
            }
            GradientSmoothness.Smooth -> {
                if (linearT < 0.5) {
                    kotlin.math.sqrt(linearT / 2.0)
                } else {
                    1.0 - kotlin.math.sqrt((1.0 - linearT) / 2.0)
                }
            }
        }

        return Color(
            red = (startColor.r + (endColor.r - startColor.r) * easedT.toFloat()).coerceIn(0f, 1f),
            green = (startColor.g + (endColor.g - startColor.g) * easedT.toFloat()).coerceIn(0f, 1f),
            blue = (startColor.b + (endColor.b - startColor.b) * easedT.toFloat()).coerceIn(0f, 1f)
        )
    }

    private fun generateFade(): List<FadeInfo> {
        val samplingFps = resolvedSamplingFps()
        val frameTime = 1000.0 / samplingFps
        val bpm = WorkspaceRepository.bpm.value
        val totalTime = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)
        val manualSteps = state.value.gradientSteps // null = INF (auto), 2-16 = manual step count

        val gradientData = state.value.gradientData.sortedBy { it.position }

        if (gradientData.size < 2) return emptyList()

        val signature = FadeSignature(
            gradientHash = gradientData.fold(gradientData.size) { acc, color ->
                (((((acc * 31 + color.position.toBits()) * 31 + color.r.toBits()) * 31 + color.g.toBits()) * 31 + color.b.toBits()) * 31 + color.smoothness.ordinal)
            },
            fps = samplingFps,
           totalTimeBits = totalTime.toDouble().toBits(),
           gradientSteps = state.value.gradientSteps
        )
        cachedFadeSignature?.let { cachedSig ->
            if (cachedSig == signature) return cachedFade
        }

        // Handle manualSteps (discrete steps generated overall in the device)
        if (manualSteps != null && manualSteps in 2..16) {
            val fade = mutableListOf<FadeInfo>()
            for (k in 0 until manualSteps) {
                val progress = k.toDouble() / (manualSteps - 1)
                val color = interpolateColor(gradientData, progress.toFloat())
                val time = (k.toDouble() / manualSteps) * totalTime
                fade.add(FadeInfo(color, time, true))
            }
            val lastColor = gradientData.last()
            val lastColorLit = lastColor.r > 0.01f || lastColor.g > 0.01f || lastColor.b > 0.01f
            if (lastColorLit) {
                fade.add(FadeInfo(Color.Black, totalTime.toDouble()))
            } else {
                fade.add(FadeInfo(Color(lastColor.r, lastColor.g, lastColor.b), totalTime.toDouble()))
            }
            cachedFadeSignature = signature
            cachedFade = fade
            return fade
        }

        colorStepBuffer.clear()
        stepCountBuffer.clear()
        cutoffBuffer.clear()
        cutoffBuffer.add(0)

        for (i in 0 until gradientData.size - 1) {
            val current = gradientData[i]
            val next = gradientData[i + 1]
            val fadeType = current.smoothness
            val segmentDurationMs = (next.position - current.position).coerceAtLeast(0f).toDouble() * totalTime

            val segmentFrames = (segmentDurationMs / frameTime).toInt().coerceAtLeast(1).coerceAtMost(10_000)

            if (fadeType == GradientSmoothness.Hold || segmentFrames == 0) {
                colorStepBuffer.add(Color(current.r, current.g, current.b))
                stepCountBuffer.add(1)
                cutoffBuffer.add(1 + cutoffBuffer.last())
            } else {
               for (k in 0 until segmentFrames) {
                   val factor = k.toDouble() / segmentFrames
                    colorStepBuffer.add(Color(
                        red = current.r + (next.r - current.r) * factor.toFloat(),
                        green = current.g + (next.g - current.g) * factor.toFloat(),
                        blue = current.b + (next.b - current.b) * factor.toFloat()
                    ))
                }

               stepCountBuffer.add(segmentFrames)
               cutoffBuffer.add(segmentFrames + cutoffBuffer.last())
            }
        }

        val lastColor = gradientData.last()
        colorStepBuffer.add(Color(lastColor.r, lastColor.g, lastColor.b))

        val lastColorLit = lastColor.r > 0.01f || lastColor.g > 0.01f || lastColor.b > 0.01f
        if (lastColorLit) {
            cutoffBuffer[cutoffBuffer.size - 1]++
            stepCountBuffer[stepCountBuffer.size - 1]++

            colorStepBuffer.add(Color.Black)
            stepCountBuffer.add(1)
            cutoffBuffer.add(1 + cutoffBuffer.last())
        }

        val fullFade = mutableListOf<FadeInfo>()
        fullFade.add(FadeInfo(colorStepBuffer[0], 0.0, gradientData.getOrNull(0)?.smoothness == GradientSmoothness.Hold))

        var j = 0
        for (i in 1 until colorStepBuffer.size) {
            if (j + 1 < cutoffBuffer.size && cutoffBuffer[j + 1] == i) j++

            if (j < gradientData.size - 1) {
                val prevTime = if (j != 0) gradientData[j].position.toDouble() * totalTime else 0.0
                val currTime = (gradientData[j].position +
                    (gradientData[j + 1].position - gradientData[j].position) *
                    (i - cutoffBuffer[j]).toDouble() / stepCountBuffer[j].coerceAtLeast(1)) * totalTime
                val nextTime = gradientData[j + 1].position.toDouble() * totalTime

                val fadeType = gradientData[j].smoothness
                val time = easeTime(fadeType, prevTime, nextTime, currTime)

                fullFade.add(FadeInfo(colorStepBuffer[i], time, fadeType == GradientSmoothness.Hold))
            }
        }

        val fade = mutableListOf<FadeInfo>()
        fade.add(fullFade.first())

        for (i in 1 until fullFade.size) {
            val cutoff = fade.last().time + frameTime

            if (cutoff < fullFade[i].time) {
                fade.add(fullFade[i])
            } else if (fade.last().time + 2 * frameTime <=
                (if (i < fullFade.size - 1) fullFade[i + 1].time else totalTime.toDouble())) {
                fade.add(fullFade[i].copy(time = cutoff))
            } else if (i == fullFade.size - 1 &&
                (fullFade[i].color.red > 0.01f || fullFade[i].color.green > 0.01f || fullFade[i].color.blue > 0.01f)) {
                fade.add(fullFade[i])
            }
        }

        fade.add(FadeInfo(colorStepBuffer.last(), totalTime.toDouble()))

        cachedFadeSignature = signature
        cachedFade = fade

        return fade
    }

    private fun scheduleFadeStep(
        signal: Signal.LED,
        ownerKey: GradientRunKey,
        runToken: Long,
        fade: List<FadeInfo>,
        nextIndex: Int
    ) {
        if (!isRunActive(ownerKey, runToken) || nextIndex >= fade.size) {
            clearRun(ownerKey, runToken)
            return
        }

        val previousIndex = if (nextIndex == 0) fade.lastIndex else nextIndex - 1
        val delayTime = if (nextIndex == 0) {
            0.0
        } else {
            (fade[nextIndex].time - fade[previousIndex].time).coerceAtLeast(0.0)
        }

        Heaven.schedule(
            delayInMs = delayTime,
            owner = this,
            identifier = ownerKey
        ) {
            if (!isRunActive(ownerKey, runToken)) {
                return@schedule
            }

            signalExit?.invoke(
                listOf(signal.copy(color = fade[nextIndex].color))
            )

            val followingIndex = when {
                nextIndex + 1 < fade.size -> nextIndex + 1
                state.value.loop -> 0
                else -> null
            }

            if (followingIndex == null) {
                clearRun(ownerKey, runToken)
                return@schedule
            }

            scheduleFadeStep(signal, ownerKey, runToken, fade, followingIndex)
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val litSignals = n.filter { it.color != Color.Black }

        n.asSequence()
            .filter { it.color == Color.Black }
            .forEach { signal ->
                val ownerKey = runKey(signal)
                if (state.value.loop && activeRunTokens.containsKey(ownerKey)) {
                    cancelRun(ownerKey)
                    signalExit?.invoke(listOf(signal))
                }
            }

        if (litSignals.isEmpty()) return

        val fade = generateFade()
        if (fade.isEmpty()) return

        litSignals.forEach { signal ->
            val ownerKey = runKey(signal)

            Heaven.cancelJobsForOwner(this, ownerKey)
            val runToken = startRun(ownerKey)

            signalExit?.invoke(
                listOf(signal.copy(color = fade[0].color))
            )

            if (fade.size == 1) {
                clearRun(ownerKey, runToken)
                return@forEach
            }

            scheduleFadeStep(
                signal = signal,
                ownerKey = ownerKey,
                runToken = runToken,
                fade = fade,
                nextIndex = 1
            )
        }
    }

    override fun onChoke() {
        activeRunTokens.clear()
        Heaven.cancelJobsForOwner(this)
    }

    companion object : ChainDeviceFactory<GradientChainDeviceState> {
        override val stateClass = GradientChainDeviceState::class
        override val serializer = GradientChainDeviceState.serializer()
        override fun create() = GradientChainDevice()
    }
}

@Serializable
data class GradientChainDeviceState(
    val gradientData: List<GradientColor> = listOf(
        GradientColor(0f, 1f, 1f, 1f), // Weiß bei Position 0
        GradientColor(1f, 0f, 0f, 0f)  // Schwarz bei Position 1
    ),
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val durationMs: Double = 0.0,
    val gate: Float = 0.5f,
    val loop: Boolean = false,
    val gradientSteps: Int? = null,
) : DeviceState() {
    @Serializable
    data class GradientColor(
        val position: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val smoothness: GradientSmoothness = GradientSmoothness.Linear,
        val rawPosition: Float? = null,

        override val selectionUUID: String = UUID.randomUUID(),
    ) : Selectable
}
