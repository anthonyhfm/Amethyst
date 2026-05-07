package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.devices.Chokeable
import dev.anthonyhfm.amethyst.ui.components.toMsValue
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.TextDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.PinchGraph
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.TimingControls
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class CopyChainDevice : LEDChainDevice<CopyChainDeviceState>(), Chokeable {
    override val state = MutableStateFlow(CopyChainDeviceState())

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
                .width(leftPanelWidth + 52.dp + (deviceState.offsets.size * 130.dp) + 1.dp),
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
                            optionToString = ::gridModeLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
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

                Separator(orientation = SeparatorOrientation.Vertical)

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
    private fun <T> CopySelectField(
        label: String,
        options: List<T>,
        selectedOption: T,
        onOptionSelected: (T) -> Unit,
        modifier: Modifier = Modifier,
        optionToString: (T) -> String = { it.toString() },
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )

            Select(
                value = optionToString(selectedOption),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                shape = SmallShape,
                triggerHeight = 24.dp,
                triggerContentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                options.forEach { option ->
                    SelectItem(
                        text = optionToString(option),
                        selected = option == selectedOption,
                        onClick = { onOptionSelected(option) },
                    )
                }
            }
        }
    }

    @Composable
    private fun ToggleOption(
        label: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
            Text(
                text = label,
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )
        }
    }

    @Composable
    private fun IconActionButton(
        icon: ImageVector,
        contentDescription: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        variant: ButtonVariant = ButtonVariant.Secondary,
    ) {
        val tint = when (variant) {
            ButtonVariant.Secondary -> Theme[colors][secondaryForeground]
            else -> Theme[colors][foreground]
        }

        Button(
            onClick = onClick,
            modifier = modifier,
            variant = variant,
            size = ButtonSize.Icon,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    private fun copyModeLabel(mode: CopyChainDeviceState.CopyMode): String = when (mode) {
        CopyChainDeviceState.CopyMode.STATIC -> "Static"
        CopyChainDeviceState.CopyMode.ANIMATE -> "Animate"
        CopyChainDeviceState.CopyMode.INTERPOLATE -> "Interpolate"
        CopyChainDeviceState.CopyMode.RANDOM_SINGLE -> "Random Single"
        CopyChainDeviceState.CopyMode.RANDOM_LOOP -> "Random Loop"
    }

    private fun gridModeLabel(mode: CopyChainDeviceState.GridMode): String = when (mode) {
        CopyChainDeviceState.GridMode.NONE -> "None"
        CopyChainDeviceState.GridMode.EDGELESS -> "Edgeless"
        CopyChainDeviceState.GridMode.FULL -> "Full"
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
            IconActionButton(
                icon = Icons.Default.Add,
                contentDescription = "Add Offset",
                onClick = onClick,
                variant = ButtonVariant.Outline,
            )
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
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color = Theme[colors][secondary])
                .border(1.dp, Theme[colors][border], RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Offset",
                    textAlign = TextAlign.Center,
                    style = Theme[typography][small],
                    color = Theme[colors][foreground],
                )

                IconActionButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Remove Offset",
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
                        .padding(end = 4.dp),
                    variant = ButtonVariant.Ghost,
                )
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
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )

                Text(
                    text = if (offset.isAbsolute) "AbsY: ${offset.absoluteY}" else "Y: ${offset.y}",
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
            }

            Box(
                modifier = Modifier,
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    IconActionButton(
                        icon = Icons.Default.ArrowUpward,
                        contentDescription = "Move up",
                        onClick = {
                            if (offset.isAbsolute) {
                                onChangeOffset(offset.copy(absoluteY = offset.absoluteY + 1))
                            } else {
                                onChangeOffset(offset.copy(y = offset.y + 1))
                            }
                        },
                    )

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                    ) {
                        IconActionButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move left",
                            onClick = {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteX = offset.absoluteX - 1))
                                } else {
                                    onChangeOffset(offset.copy(x = offset.x - 1))
                                }
                            },
                        )

                        IconActionButton(
                            icon = Icons.Default.ArrowDownward,
                            contentDescription = "Move down",
                            onClick = {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteY = offset.absoluteY - 1))
                                } else {
                                    onChangeOffset(offset.copy(y = offset.y - 1))
                                }
                            },
                        )

                        IconActionButton(
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move right",
                            onClick = {
                                if (offset.isAbsolute) {
                                    onChangeOffset(offset.copy(absoluteX = offset.absoluteX + 1))
                                } else {
                                    onChangeOffset(offset.copy(x = offset.x + 1))
                                }
                            },
                        )
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

    private fun transformSignal(signal: Signal.LED, offset: CopyChainDeviceState.Offset): Signal.LED? {
        val (targetX, targetY) = resolveCopyTarget(signal, offset)
        return transformSignal(signal, targetX, targetY)
    }

    private fun transformSignal(signal: Signal.LED, targetX: Int, targetY: Int): Signal.LED? {
        val state = state.value
        val wrapBounds = if (state.wrap) {
            resolveCopyCoordinateBounds(signal.origin, state.gridMode.toBoundsMode())
        } else {
            null
        }
        val isolateBounds = resolveCopyCoordinateBounds(signal.origin, state.isolate.toBoundsMode())

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
        if (state.isolate != CopyChainDeviceState.IsolationType.NONE) {
            Heaven.cancelJobsForOwner(this)
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
                val mapped = dev.anthonyhfm.amethyst.devices.effects.keyframes.util.Pincher.applyPinch(time.toDouble(), totalD, pinch, bilateral).toInt()
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
