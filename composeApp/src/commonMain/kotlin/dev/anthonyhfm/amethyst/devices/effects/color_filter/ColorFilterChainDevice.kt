package dev.anthonyhfm.amethyst.devices.effects.color_filter

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
import dev.anthonyhfm.amethyst.core.engine.heaven.isLit
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import dev.anthonyhfm.amethyst.core.controls.automation.CurveMode
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.devices.Automatable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

class ColorFilterChainDevice : LEDChainDevice<ColorFilterChainDeviceState>() {
    override val state = MutableStateFlow(ColorFilterChainDeviceState())
    override val helpRef = "ColorFilter"

    sealed class Params : AutomationParameter {
        object Hue : Params() {
            override val id = "hue"
            override val label = "Hue"
            override val curveMode = CurveMode.Bipolar
            override val unit = "°"
            override val displayRange = -180f..180f
            override val displayDecimals = 0
        }

        object HueTolerance : Params() {
            override val id = "hueTolerance"
            override val label = "Hue Tol"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..100f
            override val displayDecimals = 0
        }

        object Saturation : Params() {
            override val id = "saturation"
            override val label = "Saturation"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..100f
            override val displayDecimals = 0
        }

        object SaturationTolerance : Params() {
            override val id = "saturationTolerance"
            override val label = "Sat Tol"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..100f
            override val displayDecimals = 0
        }

        object Value : Params() {
            override val id = "value"
            override val label = "Value"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..100f
            override val displayDecimals = 0
        }

        object ValueTolerance : Params() {
            override val id = "valueTolerance"
            override val label = "Val Tol"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..100f
            override val displayDecimals = 0
        }
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Color Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(280.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            var beforeState = deviceState.copy()

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        automationParameter = Params.Hue,
                        title = "Hue",
                        text = "${deviceState.hue}°",
                        type = DialType.Steps(List(361) { -180 + it }),
                        value = deviceState.hue,
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(hue = value)
                            }
                        },
                        onResolveTextValue = { text ->
                            text.parseHueValue()?.let { value ->
                                applyResolvedState { it.copy(hue = value) }
                            }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(hue = value))
                        }
                    )

                    Dial(
                        automationParameter = Params.HueTolerance,
                        type = DialType.Continuous,
                        title = "Tolerance",
                        text = "${(deviceState.hueTolerance * 100).roundToInt()}%",
                        value = deviceState.hueTolerance,
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(hueTolerance = value)
                            }
                        },
                        onResolveTextValue = {
                            it.parsePercentValue()?.let { value ->
                                applyResolvedState { state -> state.copy(hueTolerance = value) }
                            }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(hueTolerance = value))
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxHeight(0.8f)) {
                    Separator(orientation = SeparatorOrientation.Vertical)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        automationParameter = Params.Saturation,
                        type = DialType.Continuous,
                        title = "Saturation",
                        text = "${(deviceState.saturation * 100).roundToInt()}%",
                        value = deviceState.saturation,
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(saturation = value)
                            }
                        },
                        onResolveTextValue = {
                            it.parsePercentValue()?.let { value ->
                                applyResolvedState { state -> state.copy(saturation = value) }
                            }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(saturation = value))
                        }
                    )

                    Dial(
                        automationParameter = Params.SaturationTolerance,
                        type = DialType.Continuous,
                        title = "Tolerance",
                        text = "${(deviceState.saturationTolerance * 100).roundToInt()}%",
                        value = deviceState.saturationTolerance,
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(saturationTolerance = value)
                            }
                        },
                        onResolveTextValue = {
                            it.parsePercentValue()?.let { value ->
                                applyResolvedState { state -> state.copy(saturationTolerance = value) }
                            }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(saturationTolerance = value))
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxHeight(0.8f)) {
                    Separator(orientation = SeparatorOrientation.Vertical)
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        automationParameter = Params.Value,
                        type = DialType.Continuous,
                        title = "Value",
                        text = "${(deviceState.value * 100).roundToInt()}%",
                        value = deviceState.value,
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(value = value)
                            }
                        },
                        onResolveTextValue = {
                            it.parsePercentValue()?.let { value ->
                                applyResolvedState { state -> state.copy(value = value) }
                            }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(value = value))
                        }
                    )

                    Dial(
                        automationParameter = Params.ValueTolerance,
                        type = DialType.Continuous,
                        title = "Tolerance",
                        text = "${(deviceState.valueTolerance * 100).roundToInt()}%",
                        value = deviceState.valueTolerance,
                        onStartValueChange = {
                            beforeState = state.value.copy()
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(valueTolerance = value)
                            }
                        },
                        onResolveTextValue = {
                            it.parsePercentValue()?.let { value ->
                                applyResolvedState { state -> state.copy(valueTolerance = value) }
                            }
                        },
                        onFinishValueChange = { value ->
                            pushStateChange(beforeState, state.value.copy(valueTolerance = value))
                        }
                    )
                }
            }
        }
    }

    private fun applyResolvedState(
        transform: (ColorFilterChainDeviceState) -> ColorFilterChainDeviceState
    ) {
        val before = state.value
        val after = transform(before)

        state.value = after
        pushStateChange(before, after)
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val rawS = state.value

        if (n.any { it.color.isLit() }) {
            triggerDialAutomations()
        }

        val autoHue = ((evaluateAutomatedDialValue(Params.Hue.id, (rawS.hue + 180f) / 360f) * 360f) - 180f).toInt()
        val autoHueTol = evaluateAutomatedDialValue(Params.HueTolerance.id, rawS.hueTolerance)
        val autoSat = evaluateAutomatedDialValue(Params.Saturation.id, rawS.saturation)
        val autoSatTol = evaluateAutomatedDialValue(Params.SaturationTolerance.id, rawS.saturationTolerance)
        val autoVal = evaluateAutomatedDialValue(Params.Value.id, rawS.value)
        val autoValTol = evaluateAutomatedDialValue(Params.ValueTolerance.id, rawS.valueTolerance)

        val s = rawS.copy(
            hue = autoHue,
            hueTolerance = autoHueTol,
            saturation = autoSat,
            saturationTolerance = autoSatTol,
            value = autoVal,
            valueTolerance = autoValTol
        )

        val filtered = n.filter { i ->
            if (i.color == Color.Transparent || i.color.alpha == 0f || !i.color.isLit()) return@filter true

            val (h, sat, v) = i.color.toHsv()

            val targetHue = (s.hue.toFloat() + 360f) % 360f
            val hueDiff = 180f - abs(abs(h - targetHue) - 180f)
            val hueMatch = (hueDiff / 180f) <= s.hueTolerance
            val satMatch = abs(sat - s.saturation) <= s.saturationTolerance
            val valMatch = abs(v - s.value) <= s.valueTolerance

            hueMatch && satMatch && valMatch
        }

        if (filtered.isNotEmpty()) {
            signalExit?.invoke(filtered)
        }
    }

    companion object : ChainDeviceFactory<ColorFilterChainDeviceState> {
        override val stateClass = ColorFilterChainDeviceState::class
        override val serializer = ColorFilterChainDeviceState.serializer()
        override fun create() = ColorFilterChainDevice()
    }
}

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val max = max(red, max(green, blue))
    val min = min(red, min(green, blue))
    val delta = max - min

    var h = 0f
    val s: Float = if (max == 0f) 0f else delta / max
    val v = max

    if (delta != 0f) {
        h = when (max) {
            red -> ((green - blue) / delta) % 6f
            green -> ((blue - red) / delta) + 2f
            else -> ((red - green) / delta) + 4f
        } * 60f

        if (h < 0f) h += 360f
    }

    return Triple(h, s, v)
}

private fun String.parseHueValue(): Int? = removeSuffix("°")
    .trim()
    .toIntOrNull()
    ?.takeIf { it in -180..180 }

private fun String.parsePercentValue(): Float? = removeSuffix("%")
    .trim()
    .toIntOrNull()
    ?.takeIf { it in 0..100 }
    ?.div(100f)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ColorFilterChainDeviceState(
    @Automatable(ColorFilterChainDevice.Params.Hue::class)
    val hue: Int = 0,

    @Automatable(ColorFilterChainDevice.Params.HueTolerance::class)
    val hueTolerance: Float = 0.05f,

    @Automatable(ColorFilterChainDevice.Params.Saturation::class)
    val saturation: Float = 1f,

    @Automatable(ColorFilterChainDevice.Params.SaturationTolerance::class)
    val saturationTolerance: Float = 0.05f,

    @Automatable(ColorFilterChainDevice.Params.Value::class)
    val value: Float = 1f,

    @Automatable(ColorFilterChainDevice.Params.ValueTolerance::class)
    val valueTolerance: Float = 0.05f,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState =
        copy(automations = automations)
}
