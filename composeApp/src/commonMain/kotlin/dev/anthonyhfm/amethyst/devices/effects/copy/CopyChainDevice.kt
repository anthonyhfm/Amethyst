package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystCheckbox
import dev.anthonyhfm.amethyst.ui.components.DropdownSelect
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PinchGraph
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.TimingControls
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class CopyChainDevice : LEDChainDevice<CopyChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(CopyChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        val leftPanelWidth = 240.dp

        AmethystDevice(
            title = "Copy",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(leftPanelWidth + 52.dp + (deviceState.offsets.size * 130.dp) + DividerDefaults.Thickness)
        ) {
            Row {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(leftPanelWidth)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DropdownSelect(
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
                        optionToString = {
                            when (it) {
                                CopyChainDeviceState.CopyMode.STATIC -> "Static"
                                CopyChainDeviceState.CopyMode.ANIMATE -> "Animate"
                                CopyChainDeviceState.CopyMode.INTERPOLATE -> "Interpolate"
                                CopyChainDeviceState.CopyMode.RANDOM_SINGLE -> "Random Single"
                                CopyChainDeviceState.CopyMode.RANDOM_LOOP -> "Random Loop"
                            }
                        },
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth()
                    )

                    DropdownSelect(
                        label = "Grid Mode",
                        options = CopyChainDeviceState.GridMode.entries,
                        selectedOption = deviceState.gridMode,
                        onOptionSelected = { mode ->
                            pushStateChange(
                                before = deviceState,
                                after = deviceState.copy(gridMode = mode)
                            )
                            state.update { it.copy(gridMode = mode) }
                        },
                        optionToString = {
                            when (it) {
                                CopyChainDeviceState.GridMode.NONE -> "None"
                                CopyChainDeviceState.GridMode.EDGELESS -> "Edgeless"
                                CopyChainDeviceState.GridMode.FULL -> "Full"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AmethystCheckbox(
                                checked = deviceState.wrap,
                                onCheckedChange = { wrap ->
                                    pushStateChange(before = deviceState, after = deviceState.copy(wrap = wrap))
                                    state.update { it.copy(wrap = wrap) }
                                }
                            )
                            Text("Wrap", style = MaterialTheme.typography.labelSmall)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AmethystCheckbox(
                                checked = deviceState.reverse,
                                onCheckedChange = { reverse ->
                                    pushStateChange(before = deviceState, after = deviceState.copy(reverse = reverse))
                                    state.update { it.copy(reverse = reverse) }
                                }
                            )
                            Text("Reverse", style = MaterialTheme.typography.labelSmall)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AmethystCheckbox(
                                checked = deviceState.infinite,
                                onCheckedChange = { infinite ->
                                    pushStateChange(before = deviceState, after = deviceState.copy(infinite = infinite))
                                    state.update { it.copy(infinite = infinite) }
                                }
                            )
                            Text("Infinite", style = MaterialTheme.typography.labelSmall)
                        }
                    }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimingControls(
                    modifier = Modifier.weight(1f),
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
                    }
                )

                PinchGraph(
                    pinch = deviceState.pinch,
                    onPinchChange = { pinch ->
                        val before = state.value
                        state.update { it.copy(pinch = pinch) }
                        pushStateChange(before, state.value)
                    },
                    bilateral = deviceState.bilateral,
                    onToggleBilateral = {
                        val before = state.value
                        state.update { it.copy(bilateral = !it.bilateral) }
                        pushStateChange(before, state.value)
                    },
                    modifier = Modifier.size(52.dp)
                )
            }
                }

                VerticalDivider()

                Row {
                    deviceState.offsets.forEachIndexed { index, offset ->
                        Offset(
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

    @Composable
    fun AddOffsetButton(
        onClick: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(52.dp),

            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onClick
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add a new device"
                )
            }
        }
    }

    @Composable
    fun Offset(
        index: Int,
        offset: CopyChainDeviceState.Offset,
        deviceState: CopyChainDeviceState,
        onChangeOffset: (CopyChainDeviceState.Offset) -> Unit
    ) {
        Column(
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight()
                .padding(start = 4.dp)
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Offset",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )

                IconButton(
                    onClick = {
                        val before = state.value
                        val after = before.copy(
                            offsets = before.offsets.filterIndexed { i, _ -> i != index }
                        )
                        pushStateChange(before, after)
                        state.update { after }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Remove Offset",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .rightClickable {
                        onChangeOffset(offset.copy(isAbsolute = !offset.isAbsolute))
                    }
            ) {
                Text(
                    text = if (offset.isAbsolute) "AbsX: ${offset.absoluteX}" else "X: ${offset.x}",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                )

                Text(
                    text = if (offset.isAbsolute) "AbsY: ${offset.absoluteY}" else "Y: ${offset.y}",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                )
            }

            Box(
                modifier = Modifier.height(76.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(30.dp)
                            .clickable {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteY = offset.absoluteY + 1))
                                } else {
                                    onChangeOffset(offset.copy(y = offset.y + 1))
                                }
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "up", modifier = Modifier.size(20.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(30.dp)
                            .clickable {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteY = offset.absoluteY - 1))
                                } else {
                                    onChangeOffset(offset.copy(y = offset.y - 1))
                                }
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "down", modifier = Modifier.size(20.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),

                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(30.dp)
                            .clickable {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteX = offset.absoluteX - 1))
                                } else {
                                    onChangeOffset(offset.copy(x = offset.x - 1))
                                }
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "left", modifier = Modifier.size(20.dp))
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(30.dp)
                            .clickable {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteX = offset.absoluteX + 1))
                                } else {
                                    onChangeOffset(offset.copy(x = offset.x + 1))
                                }
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "right", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (deviceState.mode == CopyChainDeviceState.CopyMode.INTERPOLATE) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextDial(
                        headline = "Angle",
                        text = "${offset.angle}°",
                        value = offset.angle.toFloat() / 360f,
                        onValueChange = { value ->
                            onChangeOffset(offset.copy(angle = (value * 360f).toInt()))
                        },
                        onResolveTextValue = { text ->
                            val angleText = text.removeSuffix("°").trim().toIntOrNull()
                            angleText?.let { angle ->
                                onChangeOffset(offset.copy(angle = angle))
                            }
                        },
                        modifier = Modifier
                            .rightClickable {
                                onChangeOffset(offset.copy(angle = 0))
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    private fun bresenhamLine(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()

        var x = x0
        var y = y0

        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)

        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1

        var err = dx - dy

        while (true) {
            points.add(Pair(x, y))

            if (x == x1 && y == y1) break

            val e2 = 2 * err

            if (e2 > -dy) {
                err -= dy
                x += sx
            }

            if (e2 < dx) {
                err += dx
                y += sy
            }
        }

        return points
    }

    private fun animateSignalThroughOffsetsWithIsolation(originalSignal: Signal.LED, offsets: List<CopyChainDeviceState.Offset>) {
        val signalOwner = Pair(this, "${originalSignal.x},${originalSignal.y}")
        val stepDelayMs = (state.value.timing.toMsValue(WorkspaceRepository.bpm.value) * (state.value.gate * 2)).toLong()

        val targets = mutableListOf<CopyChainDeviceState.Offset>()
        targets.add(CopyChainDeviceState.Offset(0, 0))
        targets.addAll(offsets)

        var globalStepIndex = 0

        for (i in 0 until targets.size - 1) {
            val startOffset = targets[i]
            val endOffset = targets[i + 1]

            val interpolationPoints = bresenhamLine(
                startOffset.x, startOffset.y,
                endOffset.x, endOffset.y
            )

            interpolationPoints.forEachIndexed { stepIndex, offset ->
                val copiedSignal = originalSignal.copy(
                    x = originalSignal.x + offset.first,
                    y = originalSignal.y - offset.second
                )

                if (isSignalWithinDeviceBounds(copiedSignal, state.value.gridMode)) {
                    Heaven.schedule(
                        delayInMs = (stepDelayMs * globalStepIndex).toDouble(),
                        owner = signalOwner
                    ) {
                        signalExit?.invoke(listOf(copiedSignal))
                    }
                }
                globalStepIndex++
            }

            if (i < targets.size - 2) {
                globalStepIndex--
            }
        }
    }

    private fun isSignalWithinDeviceBounds(signal: Signal.LED, gridMode: CopyChainDeviceState.GridMode): Boolean {
        if (gridMode == CopyChainDeviceState.GridMode.NONE) {
            return true
        }

        val originDevice = signal.origin as? LaunchpadViewportElement ?: return true

        val absoluteX = signal.x
        val absoluteY = signal.y

        val layout = originDevice.layout
        val deviceStartX = originDevice.position.value.x.toInt()
        val deviceStartY = originDevice.position.value.y.toInt()
        val deviceEndX = deviceStartX + originDevice.layout.cols
        val deviceEndY = deviceStartY + originDevice.layout.rows

        return when (gridMode) {
            CopyChainDeviceState.GridMode.EDGELESS -> {
                val mainGridStartX = deviceStartX + layout.mainOffsetX + layout.offsetX
                val mainGridStartY = deviceStartY + layout.mainOffsetY + layout.offsetY
                val mainGridEndX = mainGridStartX + layout.mainGridMaxX
                val mainGridEndY = mainGridStartY + layout.mainGridMaxY

                absoluteX in mainGridStartX until mainGridEndX &&
                absoluteY in mainGridStartY until mainGridEndY
            }

            CopyChainDeviceState.GridMode.FULL -> {
                absoluteX in deviceStartX until deviceEndX &&
                absoluteY in deviceStartY until deviceEndY
            }
        }
    }

    private fun filterSignalsForIsolation(signals: List<Signal.LED>): List<Signal> {
        return signals.filter { signal ->
            isSignalWithinDeviceBounds(signal, state.value.gridMode)
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        val state = state.value
        if (state.isolate != CopyChainDeviceState.IsolationType.NONE) {
            Heaven.cancelJobsForOwner(this)
        }
        
        when (state.mode) {
            CopyChainDeviceState.CopyMode.STATIC -> {
                state.offsets.forEach { offset ->
                    val transformed = transformSignals(n, offset)
                    val filtered = filterSignalsForIsolation(transformed)
                    if (filtered.isNotEmpty()) signalExit?.invoke(filtered.map { it as Signal.LED })
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

            CopyChainDeviceState.CopyMode.RANDOM_SINGLE -> {
                val allPossible = listOf(CopyChainDeviceState.Offset(0, 0)) + state.offsets
                val picked = allPossible.random()
                val transformed = transformSignals(n, picked)
                val filtered = filterSignalsForIsolation(transformed)
                if (filtered.isNotEmpty()) signalExit?.invoke(filtered.map { it as Signal.LED })
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
                            val filtered = filterSignalsForIsolation(offSignals)
                            signalExit?.invoke(filtered.map { it as Signal.LED })
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
                
                val filtered = filterSignalsForIsolation(signals)
                signalExit?.invoke(filtered.map { it as Signal.LED })
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
                    val filtered = filterSignalsForIsolation(transformed)
                    
                    val offSignals = if (lastOffset != null) {
                        val lastTransformed = transformSignals(listOf(triggerSignal.copy(color = Color.Black)), lastOffset)
                        filterSignalsForIsolation(lastTransformed)
                    } else emptyList()

                    signalExit?.invoke((offSignals + filtered).map { it as Signal.LED })
                    
                    playStep(loopOffset + stepDelayMs)
                }
            }
        }

        playStep(0.0)
    }

    private fun transformSignals(signals: List<Signal.LED>, offset: CopyChainDeviceState.Offset): List<Signal.LED> {
        val state = state.value
        return signals.map { signal ->
            var newX = if (offset.isAbsolute) offset.absoluteX else signal.x + offset.x
            var newY = if (offset.isAbsolute) offset.absoluteY else signal.y - offset.y

            if (state.wrap) {
                newX = (newX % 10 + 10) % 10
                newY = (newY % 10 + 10) % 10
            }

            signal.copy(x = newX, y = newY, origin = signal.origin)
        }
    }

    private fun renderAnimation(triggerSignals: List<Signal.LED>): List<Pair<Int, List<Signal.LED>>> {
        val state = state.value
        val stepDelayMs = (state.timing.toMsValue(WorkspaceRepository.bpm.value) * (state.gate * 2)).toInt()
        val offsets = if (state.reverse) state.offsets.reversed() else state.offsets
        
        val raw = buildList {
            if (state.reverse) {
                // Reverse: Offsets reversed, but we also need to include the "0,0" (origin) relative to the LAST offset?
                // Actually Apollo's reverse just reverses the order of signals.
                // If offsets are [O1, O2], normal is [Origin, O1, O2].
                // Reversed would be [Origin, O2, O1] or [O2, O1, Origin]?
                // Let's check Apollo: "if (Reverse) validOffsets.Reverse();"
                // validOffsets starts with s.Index (Origin), then adds offsets.
                // So if validOffsets = [Origin, O1, O2], Reverse gives [O2, O1, Origin].

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
        val targets = mutableListOf(CopyChainDeviceState.Offset(0, 0))
        targets.addAll(state.offsets)
        
        if (state.reverse) {
            targets.reverse()
        }

        val raw = buildList {
            var globalStepIndex = 0
            for (i in 0 until targets.size - 1) {
                val start = targets[i]
                val end = targets[i + 1]
                
                // When reversing, the angle should probably be inverted if it's associated with the 'end' point?
                // Apollo code: double angle = Angles[i] / 90.0 * Math.PI;
                // It uses the angle at index 'i'. 
                // If we reverse targets, we also need to reverse/adjust angles.
                
                // Actually, in Amethyst, angle is part of the Offset object.
                // If we have [T0, T1, T2] with angles [A0, A1, A2] (A0 is usually ignored as it's the start)
                // Normal: T0 -> T1 (angle A1), T1 -> T2 (angle A2)
                // Reversed: T2 -> T1 (angle ?), T1 -> T0 (angle ?)
                
                // Let's re-examine Apollo's Angle logic.
                // It has a list of Offsets and a list of Angles.
                // Insert(index, offset, angle) inserts at same index.
                // CopyCalc loop:
                // for (int i = 0; i < Offsets.Count; i++) {
                //    if (CopyMode == CopyType.Interpolate) {
                //        double angle = Angles[i] ...
                //        ... interpolation from 'source' to 'target' (Offsets[i])
                //    }
                //    px = _x; py = _y; (update source for next iteration)
                // }
                
                // So Angles[i] is the angle for the transition TO Offsets[i].
                // If we reverse validOffsets (which includes Origin at index 0), 
                // we should also probably reverse the angles but be careful with indices.

                val points = if (end.angle != 0) {
                    // If we are reversing, the arc should be flipped to maintain the same shape?
                    // Actually, if we go from B to A instead of A to B, and use the same center, the angle is -radAngle.
                    calculateArcPoints(start.x, start.y, end.x, end.y, if (state.reverse) -end.angle else end.angle)
                } else {
                    bresenhamLine(start.x, start.y, end.x, end.y)
                }
                
                points.forEachIndexed { stepIndex, point ->
                    if (i > 0 && stepIndex == 0) return@forEachIndexed
                    val offset = CopyChainDeviceState.Offset(point.first, point.second)
                    add(globalStepIndex * stepDelayMs to transformSignals(triggerSignals, offset))
                    globalStepIndex++
                }
            }
        }
        
        return applyPinchToAnimation(raw)
    }

    private fun calculateArcPoints(x0: Int, y0: Int, x1: Int, y1: Int, angle: Int): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        val radAngle = angle.toDouble() * (kotlin.math.PI / 180.0)

        val dx = (x1 - x0).toDouble()
        val dy = (y1 - y0).toDouble()
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 0.001) return listOf(x0 to y0)

        // dist = 2 * R * sin(abs(angle) / 2)
        val r = dist / (2.0 * sin(abs(radAngle) / 2.0))
        val h = r * cos(abs(radAngle) / 2.0)

        val midX = (x0 + x1) / 2.0
        val midY = (y0 + y1) / 2.0

        // Perpendicular vector
        val vx = -(dy / dist)
        val vy = dx / dist

        // Center of arc
        // If angle > 0, center is to the left of the vector (x0,y0) -> (x1,y1)
        val sign = if (angle > 0) 1.0 else -1.0
        val cx = midX + sign * h * vx
        val cy = midY + sign * h * vy

        // Angles from center to start and end
        val startAngle = atan2(y0.toDouble() - cy, x0.toDouble() - cx)

        // Number of points based on distance (Bresenham-like density)
        val steps = max(abs(dx), abs(dy)).toInt() * 2 // Increase density for curves

        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val currentAngle = startAngle + t * radAngle
            val px = (cx + r * cos(currentAngle)).roundToInt()
            val py = (cy + r * sin(currentAngle)).roundToInt()
            points.add(px to py)
        }

        return points.distinct()
    }

    private fun applyPinchToAnimation(raw: List<Pair<Int, List<Signal.LED>>>): List<Pair<Int, List<Signal.LED>>> {
        val state = state.value
        val pinch = state.pinch
        val bilateral = state.bilateral
        val totalDuration = raw.lastOrNull()?.first ?: 0

        if (totalDuration > 0 && (pinch != 0f || bilateral)) {
            val totalD = totalDuration.toDouble()
            return raw.map { (time, signals) ->
                val mapped = dev.anthonyhfm.amethyst.devices.effects.keyframes.util.Pincher.applyPinch(time.toDouble(), totalD, pinch, bilateral).toInt()
                mapped to signals
            }.distinctBy { it.first }.sortedBy { it.first }
        }
        return raw
    }

    override fun onChoke() {
        Heaven.cancelJobsForOwner(this)
        heldSignals.clear()
    }


}

@Serializable
data class CopyChainDeviceState(
    val mode: CopyMode = CopyMode.STATIC,
    val gridMode: GridMode = GridMode.NONE,
    val wrap: Boolean = false,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val gate: Float = 0.5f, // Apollo 100% = 0.5f
    val pinch: Float = 0f,
    val bilateral: Boolean = false,
    val reverse: Boolean = false,
    val infinite: Boolean = false,
    val isolate: IsolationType = IsolationType.NONE,
    val offsets: List<Offset> = emptyList(),
) : DeviceState() {
    enum class IsolationType {
        NONE,
        EDGELESS,
        FULL
    }

    enum class CopyMode {
        STATIC,
        ANIMATE,
        INTERPOLATE,
        RANDOM_SINGLE,
        RANDOM_LOOP
    }

    enum class GridMode {
        NONE,
        EDGELESS,
        FULL,
    }

    @Serializable
    data class Offset(
        val x: Int,
        val y: Int,
        val isAbsolute: Boolean = false,
        val absoluteX: Int = 0,
        val absoluteY: Int = 0,
        val angle: Int = 0
    )
}
