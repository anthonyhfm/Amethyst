package dev.anthonyhfm.amethyst.devices.effects.copy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.LEDChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.TimeDial
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import io.androidpoet.dropdown.MenuItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.math.abs

class CopyChainDevice : LEDChainDevice<CopyChainDeviceState>() {
    override val state = MutableStateFlow(CopyChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()
        var openTypePicker: Boolean by remember { mutableStateOf(false) }
        var openIsolationPicker: Boolean by remember { mutableStateOf(false) }

        AmethystDevice(
            title = "Copy",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(160.dp + 52.dp + (deviceState.offsets.size * 130.dp) +  DividerDefaults.Thickness)
        ) {
            Row {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(160.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    AssistChip(
                        onClick = {
                            openTypePicker = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Copy Type")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = when (state.value.type) {
                                    CopyChainDeviceState.CopyType.STATIC -> "Static"
                                    CopyChainDeviceState.CopyType.INTERPOLATE -> "Interpolate"
                                }
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = openTypePicker,
                        onDismissRequest = { openTypePicker = false },
                        offset = DpOffset(x = 0.dp, y = 2.dp)
                    ) {
                        CopyChainDeviceState.CopyType.entries.forEach { type ->
                            MenuItem(
                                onClick = {
                                    state.value = state.value.copy(type = type)
                                    openTypePicker = false
                                },
                                content = {
                                    Text(
                                        text = when (type) {
                                            CopyChainDeviceState.CopyType.STATIC -> "Static"
                                            CopyChainDeviceState.CopyType.INTERPOLATE -> "Interpolate"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Isolation",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        AssistChip(
                            onClick = {
                                openIsolationPicker = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Copy Type")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    text = when (state.value.isolate) {
                                        CopyChainDeviceState.IsolationType.NONE -> "None"
                                        CopyChainDeviceState.IsolationType.EDGELESS -> "Edgeless"
                                        CopyChainDeviceState.IsolationType.FULL -> "Full"
                                    }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = openIsolationPicker,
                            onDismissRequest = { openIsolationPicker = false },
                            offset = DpOffset(x = 0.dp, y = 24.dp)
                        ) {
                            CopyChainDeviceState.IsolationType.entries.forEach { type ->
                                MenuItem(
                                    onClick = {
                                        state.value = state.value.copy(isolate = type)
                                        openIsolationPicker = false
                                    },
                                    content = {
                                        Text(
                                            text = when (type) {
                                                CopyChainDeviceState.IsolationType.NONE -> "None"
                                                CopyChainDeviceState.IsolationType.EDGELESS -> "Edgeless"
                                                CopyChainDeviceState.IsolationType.FULL -> "Full"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),

                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        TimeDial(
                            headline = "Rate",
                            timing = deviceState.timing,
                            onSelectTiming = { timing, msValue ->
                                state.update {
                                    it.copy(
                                        timing = timing,
                                        delayMs = msValue
                                    )
                                }
                            }
                        )

                        TextDial(
                            headline = "Gate",
                            text = "${(deviceState.gate * 200).toInt()}%",
                            value = deviceState.gate,
                            onValueChange = { value ->
                                state.update {
                                    it.copy(gate = value)
                                }
                            },
                            onResolveTextValue = {
                                val gateText = it.removeSuffix("%").trim().toIntOrNull()

                                gateText?.let { gate ->
                                    if (gate in 0..200) {
                                        state.update {
                                            it.copy(gate = gate / 200f) // Convert to float between 0.0 and 1.0
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .rightClickable {
                                    state.update {
                                        it.copy(gate = 0.5f) // Reset gate to its original state
                                    }
                                },
                        )
                    }
                }

                VerticalDivider()

                Row {
                    deviceState.offsets.forEachIndexed { index, offset ->
                        Offset(
                            index = index,
                            offset = offset,
                            onChangeOffset = { newOffset ->
                                state.update {
                                    it.copy(
                                        offsets = it.offsets.mapIndexed { i, pair ->
                                            if (i == index) {
                                                newOffset
                                            } else {
                                                pair
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }

                    AddOffsetButton(
                        onClick = {
                            state.update {
                                it.copy(
                                    offsets = it.offsets + Pair(0, 0) // Add a new offset with default values
                                )
                            }
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
        offset: Pair<Int, Int>,
        onChangeOffset: (Pair<Int, Int>) -> Unit
    ) {
        Column(
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight()
                .padding(start = 6.dp)
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "Offset",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "X: ${offset.first}",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                )

                Text(
                    text = "Y: ${offset.second}",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .size(42.dp)
                        .clickable {
                            onChangeOffset(offset.copy(second = offset.second + 1))
                        },

                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "up")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = -6.dp),

                    horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(38.dp)
                            .clickable {
                                onChangeOffset(offset.copy(first = offset.first - 1))
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "left")
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(38.dp)
                            .clickable {
                                onChangeOffset(offset.copy(first = offset.first + 1))
                            },

                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "right")
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(y = -12.dp)
                        .clip(CircleShape)
                        .size(38.dp)
                        .clickable {
                            onChangeOffset(offset.copy(second = offset.second - 1))
                        },

                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "down")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            FilledIconButton(
                onClick = {
                    state.update {
                        it.copy(
                            offsets = it.offsets.filterIndexed { i, _ -> i != index }
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Remove Offset")
            }
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

    private fun animateSignalThroughOffsetsWithIsolation(originalSignal: Signal.LED, offsets: List<Pair<Int, Int>>) {
        val signalOwner = Pair(this, "${originalSignal.x},${originalSignal.y}")

        val targets = mutableListOf<Pair<Int, Int>>()
        targets.add(Pair(0, 0))
        targets.addAll(offsets)

        var globalStepIndex = 0

        for (i in 0 until targets.size - 1) {
            val startOffset = targets[i]
            val endOffset = targets[i + 1]

            val interpolationPoints = bresenhamLine(
                startOffset.first, startOffset.second,
                endOffset.first, endOffset.second
            )

            interpolationPoints.forEachIndexed { stepIndex, offset ->
                val copiedSignal = originalSignal.copy(
                    x = originalSignal.x + offset.first,
                    y = originalSignal.y - offset.second
                )

                if (isSignalWithinDeviceBounds(copiedSignal, state.value.isolate)) {
                    Heaven.schedule(
                        delayInMs = (state.value.delayMs * globalStepIndex).toDouble(),
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

    private fun isSignalWithinDeviceBounds(signal: Signal.LED, isolationType: CopyChainDeviceState.IsolationType): Boolean {
        if (isolationType == CopyChainDeviceState.IsolationType.NONE) {
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

        return when (isolationType) {
            CopyChainDeviceState.IsolationType.EDGELESS -> {
                val mainGridStartX = deviceStartX + layout.mainOffsetX + layout.offsetX
                val mainGridStartY = deviceStartY + layout.mainOffsetY + layout.offsetY
                val mainGridEndX = mainGridStartX + layout.mainGridMaxX
                val mainGridEndY = mainGridStartY + layout.mainGridMaxY

                absoluteX in mainGridStartX until mainGridEndX &&
                absoluteY in mainGridStartY until mainGridEndY
            }

            CopyChainDeviceState.IsolationType.FULL -> {
                absoluteX in deviceStartX until deviceEndX &&
                absoluteY in deviceStartY until deviceEndY
            }
            else -> true
        }
    }

    private fun filterSignalsForIsolation(signals: List<Signal.LED>): List<Signal> {
        return signals.filter { signal ->
            isSignalWithinDeviceBounds(signal, state.value.isolate)
        }
    }

    override fun ledSignalEnter(n: List<Signal.LED>) {
        when (state.value.type) {
            CopyChainDeviceState.CopyType.STATIC -> {
                state.value.offsets.forEach { offset ->
                    val copiedSignals = n.map { signal ->
                        signal.copy(
                            x = signal.x + offset.first,
                            y = signal.y - offset.second
                        )
                    }

                    val filteredSignals = filterSignalsForIsolation(copiedSignals)

                    if (filteredSignals.isNotEmpty()) {
                        signalExit?.invoke(filteredSignals)
                    }
                }
            }

            CopyChainDeviceState.CopyType.INTERPOLATE -> {
                if (state.value.offsets.isNotEmpty()) {
                    n.forEach { signal ->
                        animateSignalThroughOffsetsWithIsolation(signal, state.value.offsets)
                    }
                }
            }
        }

        signalExit?.invoke(n)
    }


}

@Serializable
data class CopyChainDeviceState(
    val type: CopyType = CopyType.STATIC,
    val isolate: IsolationType = IsolationType.NONE,
    val timing: Timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
    val offsets: List<Pair<Int, Int>> = emptyList(),
    val delayMs: Long = 0,
    val gate: Float = 0.5f // 100% = 0.5f, 200% = 1.0f
) : DeviceState() {
    enum class CopyType {
        STATIC,
        INTERPOLATE,
    }

    enum class IsolationType {
        NONE,
        EDGELESS,
        FULL,
    }
}