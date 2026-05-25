package dev.anthonyhfm.amethyst.devices.effects.blur

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class BlurChainDevice : LEDChainDevice<BlurChainDeviceState>() {
    override val state = MutableStateFlow(BlurChainDeviceState())

    private val activePads = mutableMapOf<Pair<Int, Int>, Signal.LED>()
    private val previousOutput = mutableMapOf<Pair<Int, Int>, Signal.LED>()

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        ChainDeviceShell(
            title = "Blur",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(200.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            var beforeState = deviceState.copy()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                ) {
                    StepTextDial(
                        headline = "Radius",
                        value = deviceState.radius,
                        steps = IntArray(8) { it + 1 }.toList(),
                        text = "${deviceState.radius}",
                        onResolveTextValue = {
                            it.trim().toIntOrNull()?.coerceIn(1, 8)?.let { v ->
                                state.update { s -> s.copy(radius = v) }
                            }
                        },
                        onStartValueChange = { beforeState = state.value.copy() },
                        onFinishValueChange = { pushStateChange(beforeState, state.value.copy(radius = it)) },
                        onValueChange = { v -> state.update { it.copy(radius = v) } },
                    )

                    Box(modifier = Modifier.fillMaxHeight(0.5f)) {
                        Separator(orientation = SeparatorOrientation.Vertical)
                    }

                    TextDial(
                        headline = "Amount",
                        text = "${(deviceState.amount * 100).roundToInt()}%",
                        value = deviceState.amount,
                        onStartValueChange = { beforeState = state.value.copy() },
                        onValueChange = { v -> state.update { it.copy(amount = v.coerceIn(0f, 1f)) } },
                        onFinishValueChange = { pushStateChange(beforeState, state.value) },
                        onResolveTextValue = {
                            it.removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100)?.let { v ->
                                state.update { s -> s.copy(amount = v / 100f) }
                            }
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BlurEnumSelectField(
                        headline = "Shape",
                        value = deviceState.shape.label,
                        modifier = Modifier.weight(1f),
                    ) {
                        BlurShape.entries.forEach { shape ->
                            SelectItem(
                                text = shape.label,
                                selected = shape == deviceState.shape,
                                onClick = {
                                    val before = state.value.copy()
                                    state.update { it.copy(shape = shape) }
                                    pushStateChange(before, state.value)
                                }
                            )
                        }
                    }

                    BlurEnumSelectField(
                        headline = "Curve",
                        value = deviceState.curve.label,
                        modifier = Modifier.weight(1f),
                    ) {
                        BlurCurve.entries.forEach { curve ->
                            SelectItem(
                                text = curve.label,
                                selected = curve == deviceState.curve,
                                onClick = {
                                    val before = state.value.copy()
                                    state.update { it.copy(curve = curve) }
                                    pushStateChange(before, state.value)
                                }
                            )
                        }
                    }
                }

                BlurEnumSelectField(
                    headline = "Edge",
                    value = deviceState.edgeHandling.label,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BlurEdgeHandling.entries.forEach { edge ->
                        SelectItem(
                            text = edge.label,
                            selected = edge == deviceState.edgeHandling,
                            onClick = {
                                val before = state.value.copy()
                                state.update { it.copy(edgeHandling = edge) }
                                pushStateChange(before, state.value)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val s = state.value

        for (signal in n) {
            val pos = Pair(signal.x, signal.y)
            if (signal.color.isLit()) {
                activePads[pos] = signal
            } else {
                activePads.remove(pos)
            }
        }

        val newOutput = computeBlurOutput(s)
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
        newOutput.forEach { (pos, sig) -> if (sig.color.isLit()) previousOutput[pos] = sig }

        signalExit?.invoke(output)
    }

    private fun computeBlurOutput(s: BlurChainDeviceState): Map<Pair<Int, Int>, Signal.LED> {
        val colorAccum = mutableMapOf<Pair<Int, Int>, Color>()
        val opacityAccum = mutableMapOf<Pair<Int, Int>, Float>()
        val signalRef = mutableMapOf<Pair<Int, Int>, Signal.LED>()

        for ((pos, signal) in activePads) {
            for ((neighbor, dist) in getNeighborhood(pos, s)) {
                val t = if (s.radius > 0) (dist / s.radius.toFloat()).coerceIn(0f, 1f) else 0f
                val attenuation = applyBlurCurve(t, s.curve)

                // Spread full source color; only opacity fades with distance
                colorAccum[neighbor] = addColors(colorAccum[neighbor] ?: Color.Black, signal.color)
                opacityAccum[neighbor] = ((opacityAccum[neighbor] ?: 0f) + signal.opacity * attenuation).coerceIn(0f, 1f)

                if (!signalRef.containsKey(neighbor)) signalRef[neighbor] = signal
            }
        }

        val result = mutableMapOf<Pair<Int, Int>, Signal.LED>()
        for ((pos, accColor) in colorAccum) {
            val dryOpacity = activePads[pos]?.opacity ?: 0f
            val wetOpacity = opacityAccum[pos] ?: 0f
            val finalOpacity = (dryOpacity + (wetOpacity - dryOpacity) * s.amount).coerceIn(0f, 1f)

            if (finalOpacity > 0f && accColor.isLit()) {
                val ref = signalRef[pos]!!
                result[pos] = ref.copy(x = pos.first, y = pos.second, color = accColor, opacity = finalOpacity, origin = this)
            }
        }
        return result
    }

    private data class NeighborResult(val pos: Pair<Int, Int>, val dist: Float)

    private fun getNeighborhood(pos: Pair<Int, Int>, s: BlurChainDeviceState): List<NeighborResult> {
        val (x, y) = pos
        val result = mutableListOf<NeighborResult>()
        for (dx in -s.radius..s.radius) {
            for (dy in -s.radius..s.radius) {
                val dist = computeDistance(dx, dy, s.shape)
                if (dist > s.radius) continue
                val nx = x + dx
                val ny = y + dy
                val finalPos = when (s.edgeHandling) {
                    BlurEdgeHandling.None -> Pair(nx, ny)

                    BlurEdgeHandling.Clamp -> {
                        if (nx < 0 || nx > 9 || ny < 0 || ny > 9) continue
                        Pair(nx, ny)
                    }

                    BlurEdgeHandling.Wrap -> Pair(((nx % 10) + 10) % 10, ((ny % 10) + 10) % 10)
                }
                result.add(NeighborResult(finalPos, dist))
            }
        }
        return result
    }

    private fun computeDistance(dx: Int, dy: Int, shape: BlurShape): Float = when (shape) {
        BlurShape.Circle -> sqrt((dx * dx + dy * dy).toFloat())
        BlurShape.Square -> max(abs(dx), abs(dy)).toFloat()
        BlurShape.Diamond -> (abs(dx) + abs(dy)).toFloat()
    }

    private fun applyBlurCurve(t: Float, curve: BlurCurve): Float = when (curve) {
        BlurCurve.Linear -> 1f - t
        BlurCurve.EaseIn -> 1f - t * t
        BlurCurve.EaseOut -> (1f - t) * (1f - t)
        BlurCurve.Bell -> exp(-4f * t * t)
    }.coerceIn(0f, 1f)

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val c = t.coerceIn(0f, 1f)
        return Color(
            a.red + (b.red - a.red) * c,
            a.green + (b.green - a.green) * c,
            a.blue + (b.blue - a.blue) * c,
        )
    }

    private fun addColors(a: Color, b: Color): Color = Color(
        (a.red + b.red).coerceIn(0f, 1f),
        (a.green + b.green).coerceIn(0f, 1f),
        (a.blue + b.blue).coerceIn(0f, 1f),
    )

    private fun Color.isLit(): Boolean = red > 0f || green > 0f || blue > 0f

    companion object : ChainDeviceFactory<BlurChainDeviceState> {
        override val stateClass = BlurChainDeviceState::class
        override val serializer = BlurChainDeviceState.serializer()
        override fun create() = BlurChainDevice()
    }
}

@Composable
private fun BlurEnumSelectField(
    headline: String,
    value: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = headline,
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
        Select(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            shape = SmallShape,
            triggerHeight = 24.dp,
            triggerContentPadding = PaddingValues(horizontal = 8.dp),
            content = content,
        )
    }
}


@Serializable
enum class BlurShape(val label: String) {
    Circle("Circle"),
    Square("Square"),
    Diamond("Diamond"),
}

@Serializable
enum class BlurCurve(val label: String) {
    Linear("Linear"),
    EaseIn("Ease In"),
    EaseOut("Ease Out"),
    Bell("Bell"),
}

@Serializable
enum class BlurEdgeHandling(val label: String) {
    None("None"),
    Clamp("Clamp"),
    Wrap("Wrap"),
}

@Serializable
data class BlurChainDeviceState(
    val radius: Int = 1,
    val shape: BlurShape = BlurShape.Circle,
    val amount: Float = 1f,
    val curve: BlurCurve = BlurCurve.Linear,
    val edgeHandling: BlurEdgeHandling = BlurEdgeHandling.None,
) : DeviceState()
