package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.devices.TimelineDuration
import dev.anthonyhfm.amethyst.devices.TimelineDurationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class CopyChainDevice : LEDChainDevice<CopyChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(CopyChainDeviceState())
    override val helpRef = "Copy"

    override fun timelineDuration(context: TimelineDurationContext): TimelineDuration {
        val current = state.value
        if (current.mode == CopyChainDeviceState.CopyMode.RANDOM_LOOP) {
            return TimelineDuration.Unbounded
        }
        if (current.mode == CopyChainDeviceState.CopyMode.STATIC || current.mode == CopyChainDeviceState.CopyMode.RANDOM_SINGLE) {
            return TimelineDuration.None
        }
        val steps = when (current.mode) {
            CopyChainDeviceState.CopyMode.ANIMATE -> current.offsets.size.coerceAtLeast(1)
            CopyChainDeviceState.CopyMode.INTERPOLATE,
            CopyChainDeviceState.CopyMode.HOLD_INTERPOLATE -> {
                current.offsets.sumOf { offset ->
                    val configuredDistance = if (offset.isAbsolute) {
                        maxOf(
                            kotlin.math.abs(offset.absoluteX) + context.canvasWidth,
                            kotlin.math.abs(offset.absoluteY) + context.canvasHeight,
                        )
                    } else {
                        maxOf(kotlin.math.abs(offset.x), kotlin.math.abs(offset.y))
                    }
                    val linearUpperBound = maxOf(
                        configuredDistance,
                        context.canvasWidth,
                        context.canvasHeight,
                    ).coerceAtLeast(1)
                    val arcMultiplier = (kotlin.math.abs(offset.angle) / 45 + 1).coerceAtLeast(1)
                    linearUpperBound * arcMultiplier
                }.coerceAtLeast(1)
            }
            else -> 0
        }
        val stepMs = current.timing.toMsValue(context.bpm.toDouble()) * (current.gate * 2f)
        return TimelineDuration.Finite((stepMs * steps).toLong().coerceAtLeast(0L))
    }

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }

        val leftPanelWidth = 280.dp

        ChainDeviceShell(
            title = "Copy",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier
                .width(leftPanelWidth + 52.dp + (130.dp * deviceState.offsets.size) + 1.dp),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            Row {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(leftPanelWidth)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CopySelectField(
                            label = "Mode",
                            options = CopyChainDeviceState.CopyMode.entries,
                            selectedOption = deviceState.mode,
                            onOptionSelected = { mode ->
                                pushStateChange(
                                    before = deviceState,
                                    after = deviceState.copy(mode = mode)
                                )
                                state.update { it.copy(mode = mode) }
                            },
                            optionToString = ::copyModeLabel,
                            modifier = Modifier.weight(1f)
                        )

                        CopySelectField(
                            label = "Isolation",
                            options = CopyChainDeviceState.IsolationType.entries,
                            selectedOption = deviceState.effectiveIsolate,
                            onOptionSelected = { mode ->
                                pushStateChange(
                                    before = deviceState,
                                    after = deviceState.copy(isolate = mode)
                                )
                                state.update { it.copy(isolate = mode) }
                            },
                            optionToString = ::isolationLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ToggleOption(
                            label = "Wrap",
                            checked = deviceState.wrap,
                            onCheckedChange = { wrap ->
                                pushStateChange(before = deviceState, after = deviceState.copy(wrap = wrap))
                                state.update { it.copy(wrap = wrap) }
                            }
                        )

                        ToggleOption(
                            label = "Reverse",
                            checked = deviceState.reverse,
                            onCheckedChange = { reverse ->
                                pushStateChange(before = deviceState, after = deviceState.copy(reverse = reverse))
                                state.update { it.copy(reverse = reverse) }
                            }
                        )

                        ToggleOption(
                            label = "Infinite",
                            checked = deviceState.infinite,
                            onCheckedChange = { infinite ->
                                pushStateChange(before = deviceState, after = deviceState.copy(infinite = infinite))
                                state.update { it.copy(infinite = infinite) }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    CopyTimeControls(
                        timing = deviceState.timing,
                        onTimingChanged = { timing ->
                            val before = state.value
                            state.update { it.copy(timing = timing) }
                            pushStateChange(before, state.value)
                        },
                        gate = deviceState.gate,
                        onGateChanged = { gate ->
                            val before = state.value
                            state.update { it.copy(gate = gate) }
                            pushStateChange(before, state.value)
                        },
                        pinch = deviceState.pinch,
                        onPinchChanged = { pinch ->
                            val before = state.value
                            state.update { it.copy(pinch = pinch) }
                            pushStateChange(before, state.value)
                        },
                        bilateral = deviceState.bilateral,
                        onToggleBilateral = {
                            val before = state.value
                            state.update { it.copy(bilateral = !it.bilateral) }
                            pushStateChange(before, state.value)
                        }
                    )
                }

                Separator(orientation = SeparatorOrientation.Vertical)

                Row {
                    deviceState.offsets.forEachIndexed { index, offset ->
                        CopyOffsetCard(
                            index = index,
                            offset = offset,
                            deviceState = deviceState,
                            onChangeOffset = { newOffset ->
                                val before = state.value
                                val after = before.copy(
                                    offsets = before.offsets.mapIndexed { i, o -> if (i == index) newOffset else o }
                                )
                                pushStateChange(before, after)
                                state.update { after }
                            },
                            onRemoveOffset = {
                                val before = state.value
                                val after = before.copy(
                                    offsets = before.offsets.filterIndexed { i, _ -> i != index }
                                )
                                pushStateChange(before, after)
                                state.update { after }
                            }
                        )
                    }

                    AddOffsetButton(
                        onClick = {
                            val before = state.value
                            val after = before.copy(offsets = before.offsets + CopyChainDeviceState.Offset(0, 0))
                            pushStateChange(before, after)
                            state.update { after }
                        }
                    )
                }
            }
        }
    }

    private fun transformSignal(signal: Signal.LED, offset: CopyChainDeviceState.Offset): Signal.LED? {
        val (targetX, targetY) = resolveCopyTarget(signal, offset)
        return transformSignal(signal, targetX, targetY)
    }

    private fun transformSignal(signal: Signal.LED, targetX: Int, targetY: Int): Signal.LED? {
        val state = state.value
        val boundsMode = state.effectiveIsolate.toBoundsMode()

        val wrapBoundsMode = if (boundsMode != CopyBoundsMode.NONE) boundsMode else CopyBoundsMode.FULL
        val wrapBounds = if (state.wrap) {
            resolveCopyCoordinateBounds(signal.origin, wrapBoundsMode)
        } else {
            null
        }

        val isolateBounds = if (!state.wrap && boundsMode != CopyBoundsMode.NONE) {
            resolveCopyCoordinateBounds(signal.origin, boundsMode)
        } else {
            null
        }

        return applyCopyCoordinatePolicy(
            signal = signal,
            rawX = targetX,
            rawY = targetY,
            wrapBounds = wrapBounds,
            isolateBounds = isolateBounds,
        )
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val state = state.value
        n.forEach { signal ->
            if (signal.color != Color.Black && state.effectiveIsolate != CopyChainDeviceState.IsolationType.NONE) {
                val identifier = signal.x * 10 + signal.y
                Heaven.cancelJobsForOwner(this, identifier)
            }
        }

        when (state.mode) {
            CopyChainDeviceState.CopyMode.STATIC -> {
                state.offsets.forEach { offset ->
                    val transformed = transformSignals(n, offset)
                    if (transformed.isNotEmpty()) signalExit?.invoke(transformed)
                }
            }

            CopyChainDeviceState.CopyMode.ANIMATE -> {
                val animation = renderAnimation(n)
                startPlayback(n, animation)
            }

            CopyChainDeviceState.CopyMode.INTERPOLATE -> {
                val animation = renderInterpolatedAnimation(n)
                startPlayback(n, animation)
            }

            CopyChainDeviceState.CopyMode.HOLD_INTERPOLATE -> {
                n.forEach { signal ->
                    val identifier = signal.x * 10 + signal.y
                    if (signal.color != Color.Black) {
                        Heaven.cancelJobsForOwner(this, identifier)
                        val animation = renderInterpolatedAnimation(listOf(signal))
                        startPlayback(listOf(signal), animation)
                    } else {
                        Heaven.cancelJobsForOwner(this, identifier)
                        val offAnimation = renderInterpolatedAnimation(listOf(signal))
                        val offSignals = offAnimation.flatMap { it.second }.distinctBy { it.x to it.y }
                        if (offSignals.isNotEmpty()) {
                            signalExit?.invoke(offSignals)
                        }
                    }
                }
            }

            CopyChainDeviceState.CopyMode.RANDOM_SINGLE -> {
                val allPossible = listOf(CopyChainDeviceState.Offset(0, 0)) + state.offsets
                val picked = allPossible.random()
                val transformed = transformSignals(n, picked)
                if (transformed.isNotEmpty()) signalExit?.invoke(transformed)
            }

            CopyChainDeviceState.CopyMode.RANDOM_LOOP -> {
                n.forEach { signal ->
                    val identifier = signal.x * 10 + signal.y
                    if (signal.color != Color.Black) {
                        heldSignals[identifier] = null
                        startRandomLoop(signal, identifier)
                    } else {
                        val lastOffset = heldSignals.remove(identifier)
                        Heaven.cancelJobsForOwner(this, identifier)

                        if (lastOffset != null) {
                            val offSignals = transformSignals(listOf(signal), lastOffset)
                            if (offSignals.isNotEmpty()) {
                                signalExit?.invoke(offSignals)
                            }
                        }
                    }
                }
            }
        }

        if (state.mode != CopyChainDeviceState.CopyMode.RANDOM_SINGLE && state.mode != CopyChainDeviceState.CopyMode.RANDOM_LOOP) {
            signalExit?.invoke(n)
        }
    }

    private val heldSignals = mutableMapOf<Int, CopyChainDeviceState.Offset?>()

    private fun startPlayback(triggerSignals: List<Signal.LED>, animation: List<Pair<Int, List<Signal.LED>>>) {
        val state = state.value
        val identifier = if (triggerSignals.size == 1) triggerSignals[0].x * 10 + triggerSignals[0].y else null

        animation.forEachIndexed { index, (time, signals) ->
            Heaven.schedule(time.toDouble(), owner = this, identifier = identifier) {
                if (state.infinite && index == animation.lastIndex && triggerSignals.any { it.color == Color.Black }) {
                    return@schedule
                }

                if (signals.isNotEmpty()) {
                    signalExit?.invoke(signals)
                }
            }
        }
    }

    private fun startRandomLoop(triggerSignal: Signal.LED, identifier: Int) {
        val state = state.value
        val stepDelayMs = (state.timing.toMsValue(WorkspaceRepository.bpm.value) * (state.gate * 2)).toDouble()
        if (stepDelayMs <= 0) return

        fun playStep(loopOffset: Double) {
            if (!heldSignals.containsKey(identifier)) return

            val lastOffset = heldSignals[identifier]
            val allPossible = listOf(CopyChainDeviceState.Offset(0, 0)) + state.offsets
            val filteredPossible = allPossible.filter { it != lastOffset }
            val picked = if (filteredPossible.isNotEmpty()) filteredPossible.random() else allPossible.random()

            heldSignals[identifier] = picked

            Heaven.schedule(loopOffset, owner = this, identifier = identifier) {
                if (heldSignals.containsKey(identifier)) {
                    val transformed = transformSignals(listOf(triggerSignal), picked)
                    val offSignals = if (lastOffset != null) {
                        transformSignals(listOf(triggerSignal.copy(color = Color.Black)), lastOffset)
                    } else emptyList()

                    val frame = offSignals + transformed
                    if (frame.isNotEmpty()) {
                        signalExit?.invoke(frame)
                    }

                    playStep(loopOffset + stepDelayMs)
                }
            }
        }

        playStep(0.0)
    }

    private fun transformSignals(signals: List<Signal.LED>, offset: CopyChainDeviceState.Offset): List<Signal.LED> {
        return signals.mapNotNull { signal ->
            transformSignal(signal, offset)
        }
    }

    private fun renderAnimation(triggerSignals: List<Signal.LED>): List<Pair<Int, List<Signal.LED>>> {
        val state = state.value
        val stepDelayMs = (state.timing.toMsValue(WorkspaceRepository.bpm.value) * (state.gate * 2)).toInt()
        val offsets = if (state.reverse) state.offsets.reversed() else state.offsets

        val raw = buildList {
            if (state.reverse) {
                val validOffsets = mutableListOf(CopyChainDeviceState.Offset(0, 0))
                state.offsets.forEach { validOffsets.add(it) }
                validOffsets.reverse()

                validOffsets.forEachIndexed { index, offset ->
                    add(index * stepDelayMs to transformSignals(triggerSignals, offset))
                }
            } else {
                add(0 to triggerSignals)
                offsets.forEachIndexed { index, offset ->
                    add((index + 1) * stepDelayMs to transformSignals(triggerSignals, offset))
                }
            }
        }

        return applyPinchToAnimation(raw)
    }

    private fun renderInterpolatedAnimation(triggerSignals: List<Signal.LED>): List<Pair<Int, List<Signal.LED>>> {
        val state = state.value
        val stepDelayMs = (state.timing.toMsValue(WorkspaceRepository.bpm.value) * (state.gate * 2)).toInt()
        val raw = buildInterpolatedCopyFrames(
            triggerSignals = triggerSignals,
            offsets = state.offsets,
            reverse = state.reverse,
            transformSignal = ::transformSignal,
        ).mapIndexed { index, signals ->
            index * stepDelayMs to signals
        }

        return applyPinchToAnimation(raw)
    }

    private fun applyPinchToAnimation(raw: List<Pair<Int, List<Signal.LED>>>): List<Pair<Int, List<Signal.LED>>> {
        val state = state.value
        val pinch = state.pinch
        val bilateral = state.bilateral
        val totalDuration = raw.lastOrNull()?.first ?: 0

        if (totalDuration > 0 && (pinch != 0f || bilateral)) {
            val totalD = totalDuration.toDouble()
            return raw.map { (time, signals) ->
                val mapped = dev.anthonyhfm.amethyst.devices.effects.keyframes.util.Pincher.applyPinch(
                    time.toDouble(),
                    totalD,
                    pinch,
                    bilateral
                ).toInt()
                mapped to signals
            }.groupBy { it.first }
                .map { (time, frames) -> time to frames.flatMap { it.second } }
                .sortedBy { it.first }
        }
        return raw
    }

    override fun onChoke() {
        Heaven.cancelJobsForOwner(this)
        heldSignals.clear()
    }

    companion object : ChainDeviceFactory<CopyChainDeviceState> {
        override val stateClass = CopyChainDeviceState::class
        override val serializer = CopyChainDeviceState.serializer()
        override fun create() = CopyChainDevice()
    }
}
