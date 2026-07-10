package dev.anthonyhfm.amethyst.devices.effects.composition.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composeunstyled.UnstyledButton
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tooltip
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlaybackOptions(
    device: CompositionChainDevice,
) {
    val state by device.state.collectAsState()
    val options = state.playbackOptions
    val isPlaying = device.isPlaying()
    val progress = device.playbackProgress()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(DefaultShape)
            .background(Theme[chainColorTokens][chainSurface])
            .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaybackScrubber(
            progress = progress,
            durationMs = device.playbackDurationMs(),
            onSeek = device::seekTo,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaybackIconButton(
                onClick = { if (isPlaying) device.pause() else device.play() },
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Composition" else "Play Composition",
                selected = isPlaying,
                isPrimary = true,
            )
            PlaybackIconButton(
                onClick = { device.updatePlaybackOptions { it.copy(repeat = !it.repeat) } },
                imageVector = Icons.Default.Repeat,
                contentDescription = if (options.repeat) "Disable Repeat" else "Enable Repeat",
                selected = options.repeat,
            )

            Spacer(Modifier.weight(1f))

            TimingControl(
                timing = options.timing,
                onTimingChange = { timing -> device.updatePlaybackOptions { it.copy(timing = timing) } },
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Theme[chainColorTokens][chainBorder]),
            )
            GateControl(
                gate = options.gate,
                onGateChange = { gate -> device.updatePlaybackOptions { it.copy(gate = gate) } },
            )
        }
    }
}

@Composable
private fun TimingControl(
    timing: Timing,
    onTimingChange: (Timing) -> Unit,
) {
    val timingState = rememberUpdatedState(timing)

    LabeledStepControl(
        label = "Length",
        value = timing.displayText(),
        onDecrease = {
            val current = timingState.value
            val next = when (current) {
                is Timing.Rythm -> {
                    val entries = Timing.Rythm.RythmTiming.entries
                    val index = entries.indexOf(current.timing).takeIf { it >= 0 } ?: entries.indexOf(Timing.Rythm.RythmTiming._1_4)
                    Timing.Rythm(entries[(index - 1).coerceIn(0, entries.lastIndex)])
                }
                is Timing.Duration -> {
                    val nextMs = (current.duration.inWholeMilliseconds - 25L).coerceIn(25L, 10_000L)
                    Timing.Duration(nextMs.milliseconds)
                }
            }
            if (next != current) onTimingChange(next)
        },
        onIncrease = {
            val current = timingState.value
            val next = when (current) {
                is Timing.Rythm -> {
                    val entries = Timing.Rythm.RythmTiming.entries
                    val index = entries.indexOf(current.timing).takeIf { it >= 0 } ?: entries.indexOf(Timing.Rythm.RythmTiming._1_4)
                    Timing.Rythm(entries[(index + 1).coerceIn(0, entries.lastIndex)])
                }
                is Timing.Duration -> Timing.Duration((current.duration.inWholeMilliseconds + 25L).coerceIn(25L, 10_000L).milliseconds)
            }
            if (next != current) onTimingChange(next)
        },
        onDragSteps = { steps ->
            val current = timingState.value
            val next = when (current) {
                is Timing.Rythm -> {
                    val entries = Timing.Rythm.RythmTiming.entries
                    val index = entries.indexOf(current.timing).takeIf { it >= 0 } ?: entries.indexOf(Timing.Rythm.RythmTiming._1_4)
                    Timing.Rythm(entries[(index + steps).coerceIn(0, entries.lastIndex)])
                }
                is Timing.Duration -> Timing.Duration((current.duration.inWholeMilliseconds + steps * 25L).coerceIn(25L, 10_000L).milliseconds)
            }
            if (next != current) onTimingChange(next)
        },
    )
}

@Composable
private fun GateControl(
    gate: Float,
    onGateChange: (Float) -> Unit,
) {
    val gateState = rememberUpdatedState(gate)

    LabeledStepControl(
        label = "Gate",
        value = "${(gate * 200).roundToInt()}%",
        onDecrease = {
            val nextPercent = ((gateState.value * 200).roundToInt() - 5).coerceIn(0, 200)
            val nextGate = nextPercent / 200f
            if (nextGate != gateState.value) onGateChange(nextGate)
        },
        onIncrease = {
            val nextPercent = ((gateState.value * 200).roundToInt() + 5).coerceIn(0, 200)
            val nextGate = nextPercent / 200f
            if (nextGate != gateState.value) onGateChange(nextGate)
        },
        onDragSteps = { steps ->
            val nextPercent = ((gateState.value * 200).roundToInt() + steps * 5).coerceIn(0, 200)
            val nextGate = nextPercent / 200f
            if (nextGate != gateState.value) onGateChange(nextGate)
        },
    )
}

@Composable
private fun PlaybackScrubber(
    progress: Float,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Slider(value = progress, onValueChange = onSeek, modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlaybackTime((progress * durationMs).toLong()), style = Theme[typography][small], color = Theme[colors][foreground])
            Text(formatPlaybackTime(durationMs), style = Theme[typography][small], color = Theme[colors][mutedForeground])
        }
    }
}

@Composable
private fun LabeledStepControl(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDragSteps: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
        CompactStepControl(
            value = value,
            decreaseDescription = "Decrease $label",
            increaseDescription = "Increase $label",
            onDecrease = onDecrease,
            onIncrease = onIncrease,
            onDragSteps = onDragSteps,
        )
    }
}

@Composable
private fun CompactStepControl(
    value: String,
    decreaseDescription: String,
    increaseDescription: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDragSteps: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .width(84.dp)
            .clip(DefaultShape)
            .background(Theme[colors][secondary])
            .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepIconButton(Icons.Default.Remove, decreaseDescription, onDecrease)
        Box(
            modifier = Modifier
                .width(36.dp)
                .pointerInput(Unit) {
                var accumulated = 0f
                detectDragGestures(
                    onDragStart = { accumulated = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += -dragAmount.y / 8f
                        val steps = accumulated.toInt()
                        if (steps != 0) {
                            onDragSteps(steps)
                            accumulated -= steps
                        }
                    },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )
        }
        StepIconButton(Icons.Default.Add, increaseDescription, onIncrease)
    }
}

@Composable
private fun StepIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    UnstyledButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp),
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    ) { Icon(icon, contentDescription = description, modifier = Modifier.size(14.dp), tint = Theme[colors][mutedForeground]) }
}

@Composable
private fun PlaybackIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    selected: Boolean,
    isPrimary: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected || isPrimary -> Theme[colors][primary]
        hovered -> Theme[colors][secondary]
        else -> Theme[colors][secondary].copy(alpha = 0.65f)
    }
    Tooltip(text = contentDescription, anchor = {
        UnstyledButton(
            onClick = onClick,
            shape = CircleShape,
            interactionSource = interactionSource,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier
                .size(40.dp)
                .shadow(if (selected || isPrimary) 5.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(background)
                .border(1.dp, if (selected || isPrimary) Color.Transparent else Theme[chainColorTokens][chainBorder], CircleShape)
                .pointerHoverIcon(PointerIcon.Hand),
        ) {
            Icon(imageVector, contentDescription = contentDescription, modifier = Modifier.size(if (isPrimary) 20.dp else 18.dp), tint = if (selected || isPrimary) Theme[colors][primaryForeground] else Theme[colors][foreground])
        }
    })
}

private fun Timing.displayText(): String = when (this) {
    is Timing.Rythm -> timing.text
    is Timing.Duration -> "${duration.inWholeMilliseconds} ms"
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000).toInt()
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
