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
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import dev.anthonyhfm.amethyst.core.controls.automation.CurveMode
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.devices.Automatable
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext

class AdjustChainDevice : LEDChainDevice<AdjustChainDeviceState>() {
    override fun timelineDuration(context: TimelineDurationContext) =
        TimelineDuration.None
    override val state = MutableStateFlow(AdjustChainDeviceState())
    override val helpRef = "Adjust"

    sealed class Params : AutomationParameter {
        object Brightness : Params() {
            override val id = "brightness"
            override val label = "Brightness"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..200f
            override val displayDecimals = 0
        }

        object Contrast : Params() {
            override val id = "contrast"
            override val label = "Contrast"
            override val curveMode = CurveMode.Unipolar
            override val unit = "%"
            override val displayRange = 0f..200f
            override val displayDecimals = 0
        }

        object Temperature : Params() {
            override val id = "temperature"
            override val label = "Temp"
            override val curveMode = CurveMode.Bipolar
            override val displayRange = -100f..100f
            override val displayDecimals = 0
        }

        object Tint : Params() {
            override val id = "tint"
            override val label = "Tint"
            override val curveMode = CurveMode.Bipolar
            override val displayRange = -100f..100f
            override val displayDecimals = 0
        }
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Col 1: Brightness + Contrast
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Dial(
                        automationParameter = Params.Brightness,
                        type = DialType.Continuous,
                        title = "Brightness",
                        text = "${(deviceState.brightness * 100).roundToInt()}%",
                        value = deviceState.brightness / 2f,
                        defaultValue = 0.5f,
                        onValueChange = { value ->
                            state.update {
                                it.copy(
                                    brightness = (value * 2f).coerceIn(0f, 2f)
                                )
                            }
                        },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..200 }
                                ?.let { v -> applyResolved { it.copy(brightness = v / 100f) } }
                        },
                    )
                    Dial(
                        automationParameter = Params.Contrast,
                        type = DialType.Continuous,
                        title = "Contrast",
                        text = "${(deviceState.contrast * 100).roundToInt()}%",
                        value = deviceState.contrast / 2f,
                        defaultValue = 0.5f,
                        onValueChange = { value -> state.update { it.copy(contrast = (value * 2f).coerceIn(0f, 2f)) } },
                        onResolveTextValue = { text ->
                            text.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..200 }
                                ?.let { v -> applyResolved { it.copy(contrast = v / 100f) } }
                        },
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
                    Dial(
                        automationParameter = Params.Temperature,
                        title = "Temp",
                        text = "${(deviceState.temperature * 100).toInt()}",
                        type = DialType.Steps(List(201) { -100 + it }),
                        value = (deviceState.temperature * 100).toInt(),
                        defaultValue = 0,
                        onValueChange = { value -> state.update { it.copy(temperature = value / 100f) } },
                        onResolveTextValue = { text ->
                            text.trim().toIntOrNull()?.takeIf { it in -100..100 }
                                ?.let { v -> applyResolved { it.copy(temperature = v / 100f) } }
                        },
                    )
                    Dial(
                        automationParameter = Params.Tint,
                        title = "Tint",
                        text = "${(deviceState.tint * 100).toInt()}",
                        type = DialType.Steps(List(201) { -100 + it }),
                        value = (deviceState.tint * 100).toInt(),
                        defaultValue = 0,
                        onValueChange = { value -> state.update { it.copy(tint = value / 100f) } },
                        onResolveTextValue = { text ->
                            text.trim().toIntOrNull()?.takeIf { it in -100..100 }
                                ?.let { v -> applyResolved { it.copy(tint = v / 100f) } }
                        },
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
        val rawS = state.value

        if (n.any { it.color.isLit() }) {
            triggerDialAutomations()
        }

        val autoB = evaluateAutomatedDialValue(Params.Brightness.id, rawS.brightness / 2f) * 2f
        val autoC = evaluateAutomatedDialValue(Params.Contrast.id, rawS.contrast / 2f) * 2f
        val autoTemp = (evaluateAutomatedDialValue(Params.Temperature.id, (rawS.temperature + 1f) / 2f) * 2f) - 1f
        val autoTint = (evaluateAutomatedDialValue(Params.Tint.id, (rawS.tint + 1f) / 2f) * 2f) - 1f
        val s = rawS.copy(brightness = autoB, contrast = autoC, temperature = autoTemp, tint = autoTint)

        signalExit?.invoke(n.map { signal ->
            if (signal.color == Color.Transparent || signal.color == Color.Black || signal.opacity <= 0f || !signal.color.isLit()) {
                signal
            } else {
                signal.copy(color = applyAdjust(signal.color, s))
            }
        })
    }

    private fun applyAdjust(color: Color, s: AdjustChainDeviceState): Color {
        var r = color.red
        var g = color.green
        var b = color.blue

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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AdjustChainDeviceState(
    @Automatable(AdjustChainDevice.Params.Brightness::class)
    val brightness: Float = 1f,

    @Automatable(AdjustChainDevice.Params.Contrast::class)
    val contrast: Float = 1f,

    @Automatable(AdjustChainDevice.Params.Temperature::class)
    val temperature: Float = 0f,

    @Automatable(AdjustChainDevice.Params.Tint::class)
    val tint: Float = 0f,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState =
        copy(automations = automations)
}
