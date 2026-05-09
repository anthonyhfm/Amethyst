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

    private data class FadeSignature(
        val gradientHash: Int,
        val fps: Int,
        val totalTimeBits: Long
    )

    private var cachedFadeSignature: FadeSignature? = null
    private var cachedFade: List<FadeInfo> = emptyList()
    private val colorStepBuffer = mutableListOf<Color>()
    private val stepCountBuffer = mutableListOf<Int>()
    private val cutoffBuffer = mutableListOf<Int>()
    private val activeRunTokens = mutableMapOf<String, Long>()
    private var nextRunToken = 0L

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
        val beforeGradientData = remember(deviceState.gradientData.map { it.selectionUUID }) {
            mutableStateOf<List<GradientChainDeviceState.GradientColor>?>(null)
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
                    width = if (selectedColor != null) {
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
                                state.update { currentState ->
                                    val updatedList = currentState.gradientData.map { currentColor ->
                                        val newPosition = data.find { it.selectionUUID == currentColor.selectionUUID }?.position
                                        if (newPosition != null) {
                                            currentColor.copy(position = newPosition)
                                        } else {
                                            currentColor
                                        }
                                    }
                                    currentState.copy(gradientData = updatedList)
                                }
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
                                beforeGradientData.value = state.value.gradientData.map { it.copy() }
                            },
                            onGradientDragFinish = {
                                val before = beforeGradientData.value
                                if (before != null) {
                                    val after = state.value.gradientData
                                    val positionsChanged = before.map { it.selectionUUID to it.position } != after.map { it.selectionUUID to it.position }
                                    if (positionsChanged) {
                                        pushStateChange(
                                            before = state.value.copy(gradientData = before),
                                            after = state.value
                                        )
                                    }
                                }
                                beforeGradientData.value = null
                            },
                            onSmoothnessChange = { selectionUUID, smoothness ->
                                val before = state.value
                                val updatedColors = state.value.gradientData.map { color ->
                                    if (color.selectionUUID == selectionUUID) {
                                        color.copy(smoothness = smoothness)
                                    } else {
                                        color
                                    }
                                }
                                state.update { it.copy(gradientData = updatedColors) }
                                pushStateChange(before, state.value)
                            }
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

                if (selectedColor != null && colorPickerState != null) {
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

    private fun startRun(ownerKey: String): Long {
        val runToken = ++nextRunToken
        activeRunTokens[ownerKey] = runToken
        return runToken
    }

    private fun isRunActive(ownerKey: String, runToken: Long): Boolean {
        return activeRunTokens[ownerKey] == runToken
    }

    private fun clearRun(ownerKey: String, runToken: Long? = null) {
        if (runToken == null || activeRunTokens[ownerKey] == runToken) {
            activeRunTokens.remove(ownerKey)
        }
    }

    private fun cancelRun(ownerKey: String) {
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

    private fun generateFade(): List<FadeInfo> {
        val samplingFps = resolvedSamplingFps()
        val frameTime = 1000.0 / samplingFps
        val bpm = WorkspaceRepository.bpm.value
        val totalTime = state.value.timing.toMsValue(bpm) * (state.value.gate * 2)

        val gradientData = state.value.gradientData.sortedBy { it.position }

        if (gradientData.size < 2) return emptyList()

        val signature = FadeSignature(
            gradientHash = gradientData.fold(gradientData.size) { acc, color ->
                (((((acc * 31 + color.position.toBits()) * 31 + color.r.toBits()) * 31 + color.g.toBits()) * 31 + color.b.toBits()) * 31 + color.smoothness.ordinal)
            },
            fps = samplingFps,
            totalTimeBits = totalTime.toDouble().toBits()
        )
        cachedFadeSignature?.let { cachedSig ->
            if (cachedSig == signature) return cachedFade
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
            val targetFrames = (segmentDurationMs / frameTime).toInt().coerceAtLeast(1)

            if (fadeType == GradientSmoothness.Hold || targetFrames == 0) {
                colorStepBuffer.add(Color(current.r, current.g, current.b))
                stepCountBuffer.add(1)
                cutoffBuffer.add(1 + cutoffBuffer.last())
            } else {
                val frames = targetFrames.coerceAtMost(10_000)
                for (k in 0 until frames) {
                    val factor = k.toDouble() / frames
                    colorStepBuffer.add(Color(
                        red = current.r + (next.r - current.r) * factor.toFloat(),
                        green = current.g + (next.g - current.g) * factor.toFloat(),
                        blue = current.b + (next.b - current.b) * factor.toFloat()
                    ))
                }

                stepCountBuffer.add(frames)
                cutoffBuffer.add(frames + cutoffBuffer.last())
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
        ownerKey: String,
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
        val fade = generateFade()
        if (fade.isEmpty()) return

        n.forEach { signal ->
            val ownerKey = "${signal.x},${signal.y}"

            if (signal.color != Color.Black) {
                Heaven.cancelJobsForOwner(this, ownerKey)
                val runToken = startRun(ownerKey)

                // Emit initial color immediately
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
            } else if (state.value.loop) {
                cancelRun(ownerKey)
            }
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
) : DeviceState() {
    @Serializable
    data class GradientColor(
        val position: Float,
        val r: Float,
        val g: Float,
        val b: Float,
        val smoothness: GradientSmoothness = GradientSmoothness.Linear,

        override val selectionUUID: String = UUID.randomUUID(),
    ) : Selectable
}
