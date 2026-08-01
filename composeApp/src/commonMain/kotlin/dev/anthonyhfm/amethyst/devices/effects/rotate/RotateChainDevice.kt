package dev.anthonyhfm.amethyst.devices.effects.rotate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import dev.anthonyhfm.amethyst.core.controls.automation.CurveMode
import dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.Automatable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.heaven.isLit
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class RotateChainDevice : LEDChainDevice<RotateChainDeviceState>() {
    override fun timelineDuration(context: TimelineDurationContext) =
        TimelineDuration.None
    override val state = MutableStateFlow(RotateChainDeviceState())
    override val helpRef = "Rotate"

    private val lock = SynchronizedObject()
    private val activePads = mutableMapOf<Pair<Int, Int>, Signal.LED>()
    private val previousOutput = mutableMapOf<Pair<Int, Int>, Signal.LED>()

    sealed class Params : AutomationParameter {
        object Angle : Params() {
            override val id = "angle"
            override val label = "Angle"
            override val curveMode = CurveMode.Unipolar
            override val unit = "°"
            override val displayRange = 0f..360f
            override val displayDecimals = 0
        }
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        ChainDeviceShell(
            title = "Rotate",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier.width(150.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f),

                    contentAlignment = Alignment.Center
                ) {
                    FlatDial(
                        automationParameter = Params.Angle,
                        type = DialType.Knob,
                        title = "Angle",
                        value = (deviceState.angleDegrees % 360f + 360f) % 360f / 360f,
                        defaultValue = 0f,
                        text = "${deviceState.angleDegrees.roundToInt()}°",
                        onValueChange = { rawNorm ->
                            val rawAngle = rawNorm * 360f
                            val snappedAngle = snapToCleanAngle(rawAngle)
                            val before = state.value
                            state.update { it.copy(angleDegrees = snappedAngle) }
                            pushStateChange(before, state.value)
                        },
                        onResolveTextValue = { textValue ->
                            textValue.removeSuffix("°").trim().toFloatOrNull()?.let { angle ->
                                val normalized = (angle % 360f + 360f) % 360f
                                val before = state.value
                                state.update { it.copy(angleDegrees = normalized) }
                                pushStateChange(before, state.value)
                            }
                        }
                    )
                }

                Separator()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = deviceState.antiAlias,
                        onCheckedChange = { checked ->
                            val before = state.value
                            state.update { it.copy(antiAlias = checked) }
                            pushStateChange(before, state.value)
                        },
                        size = 18.dp,
                        iconSize = 14.dp,
                    )

                    Text(
                        text = "Anti-Alias",
                        style = Theme[typography][small],
                        color = Theme[colors][foreground]
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = deviceState.isolate,
                        onCheckedChange = { checked ->
                            val before = state.value
                            state.update { it.copy(isolate = checked) }
                            pushStateChange(before, state.value)
                        },
                        size = 18.dp,
                        iconSize = 14.dp,
                    )

                    Text(
                        text = "Isolate",
                        style = Theme[typography][small],
                        color = Theme[colors][foreground]
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = deviceState.bypass,
                        onCheckedChange = { checked ->
                            val before = state.value
                            state.update { it.copy(bypass = checked) }
                            pushStateChange(before, state.value)
                        },
                        size = 18.dp,
                        iconSize = 14.dp,
                    )

                    Text(
                        text = "Bypass",
                        style = Theme[typography][small],
                        color = Theme[colors][foreground]
                    )
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        synchronized(lock) {
            val rawS = state.value
            val autoNorm = evaluateAutomatedDialValue(Params.Angle.id, (rawS.angleDegrees % 360f + 360f) % 360f / 360f)
            val angle = ((autoNorm * 360f) % 360f + 360f) % 360f

            for (signal in n) {
                val pos = Pair(signal.x, signal.y)
                if (signal.color.isLit() && signal.opacity > 0f) {
                    activePads[pos] = signal
                } else {
                    activePads.remove(pos)
                }
            }

            val newOutput = computeRotatedOutput(rawS, angle)
            val output = mutableListOf<Signal.LED>()

            for ((_, signal) in newOutput) {
                output.add(signal)
            }

            for ((pos, signal) in previousOutput) {
                if (!newOutput.containsKey(pos)) {
                    output.add(signal.copy(color = Color.Black, opacity = 1f))
                }
            }

            previousOutput.clear()
            newOutput.forEach { (pos, sig) ->
                if (sig.color.isLit() && sig.opacity > 0f) {
                    previousOutput[pos] = sig
                }
            }

            signalExit?.invoke(output)
        }
    }

    private fun computeRotatedOutput(s: RotateChainDeviceState, angle: Float): Map<Pair<Int, Int>, Signal.LED> {
        if (s.bypass) {
            return activePads.toMap()
        }

        val result = mutableMapOf<Pair<Int, Int>, Signal.LED>()

        for ((_, signal) in activePads) {
            val bounds = resolveBounds(signal, s.isolate)
            val cx = bounds.first.x + (bounds.second.width - 1) / 2f
            val cy = bounds.first.y + (bounds.second.height - 1) / 2f

            val rad = angle * kotlin.math.PI.toFloat() / 180f
            val cos = kotlin.math.cos(rad)
            val sin = kotlin.math.sin(rad)

            val rx = signal.x - cx
            val ry = signal.y - cy

            val fx = cx + rx * cos + ry * sin
            val fy = cy - rx * sin + ry * cos

            if (!s.antiAlias || (angle % 90f == 0f)) {
                val gx = fx.roundToInt()
                val gy = fy.roundToInt()
                if (isInBounds(gx, gy, bounds)) {
                    val targetPos = Pair(gx, gy)
                    val newSig = signal.copy(x = gx, y = gy)
                    combineSignal(result, targetPos, newSig)
                }
            } else {
                val x0 = floor(fx).toInt()
                val y0 = floor(fy).toInt()
                val dx = fx - x0
                val dy = fy - y0

                val weights = listOf(
                    Triple(x0, y0, (1f - dx) * (1f - dy)),
                    Triple(x0 + 1, y0, dx * (1f - dy)),
                    Triple(x0, y0 + 1, (1f - dx) * dy),
                    Triple(x0 + 1, y0 + 1, dx * dy)
                )

                for ((gx, gy, w) in weights) {
                    if (w > 0.01f && isInBounds(gx, gy, bounds)) {
                        val targetPos = Pair(gx, gy)
                        val newSig = signal.copy(x = gx, y = gy, opacity = signal.opacity * w)
                        combineSignal(result, targetPos, newSig)
                    }
                }
            }
        }
        return result
    }

    private fun combineSignal(
        map: MutableMap<Pair<Int, Int>, Signal.LED>,
        pos: Pair<Int, Int>,
        signal: Signal.LED
    ) {
        val existing = map[pos]
        if (existing == null) {
            map[pos] = signal
        } else {
            val mergedColor = addColors(existing.color, signal.color)
            val mergedOpacity = (existing.opacity + signal.opacity).coerceIn(0f, 1f)
            map[pos] = existing.copy(color = mergedColor, opacity = mergedOpacity)
        }
    }

    private fun addColors(a: Color, b: Color): Color = Color(
        (a.red + b.red).coerceIn(0f, 1f),
        (a.green + b.green).coerceIn(0f, 1f),
        (a.blue + b.blue).coerceIn(0f, 1f),
    )

    private fun isInBounds(x: Int, y: Int, bounds: Pair<IntOffset, IntSize>): Boolean {
        val minX = bounds.first.x
        val minY = bounds.first.y
        val maxX = minX + bounds.second.width - 1
        val maxY = minY + bounds.second.height - 1
        return x in minX..maxX && y in minY..maxY
    }

    private fun snapToCleanAngle(angle: Float, threshold: Float = 6f): Float {
        val cleanAngles = listOf(0f, 90f, 180f, 270f, 360f)
        for (clean in cleanAngles) {
            if (abs(angle - clean) <= threshold) {
                return if (clean == 360f) 0f else clean
            }
        }
        return angle % 360f
    }

    private fun resolveBounds(signal: Signal.LED, isolate: Boolean): Pair<IntOffset, IntSize> {
        if (!isolate) return WorkspaceRepository.bounds

        val device = signal.origin as? LaunchpadViewportElement ?: return WorkspaceRepository.bounds
        return Pair(
            first = IntOffset(
                x = device.position.value.x.toInt(),
                y = device.position.value.y.toInt(),
            ),
            second = IntSize(
                width = device.layout.cols,
                height = device.layout.rows,
            )
        )
    }

    companion object : ChainDeviceFactory<RotateChainDeviceState> {
        override val stateClass = RotateChainDeviceState::class
        override val serializer = RotateChainDeviceState.serializer()
        override fun create() = RotateChainDevice()
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RotateChainDeviceState(
    @ProtoNumber(1)
    val bypass: Boolean = false,
    @ProtoNumber(2)
    val isolate: Boolean = false,
    @ProtoNumber(3)
    val mode: RotateMode = RotateMode.DEGREES_90,
    @ProtoNumber(4)
    val antiAlias: Boolean = true,
    @Automatable(RotateChainDevice.Params.Angle::class)
    @ProtoNumber(5)
    val angleDegrees: Float = 90f,

    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @ProtoNumber(6)
    override val automations: Map<String, DialAutomationLane> = emptyMap(),
) : DeviceState() {
    enum class RotateMode {
        DEGREES_90,
        DEGREES_180,
        DEGREES_270,
    }

    override fun withAutomations(automations: Map<String, DialAutomationLane>): DeviceState =
        copy(automations = automations)
}
