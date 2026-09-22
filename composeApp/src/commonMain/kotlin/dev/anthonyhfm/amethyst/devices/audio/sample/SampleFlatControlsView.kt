package dev.anthonyhfm.amethyst.devices.audio.sample

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import org.jetbrains.compose.resources.stringResource
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

private enum class SampleControlPage { Envelope, Playback, Loop }

@Composable
fun SampleFlatControlsView(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    volumeMinDb: Float,
    volumeRangeDb: Float,
    volumeMaxDb: Float,
    onPushStateChange: (before: SampleChainDeviceState, after: SampleChainDeviceState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeDurationMs = (
        deviceState.totalDurationMs * (deviceState.endPosition - deviceState.startPosition)
    ).coerceAtLeast(1f)
    var page by remember { mutableStateOf(SampleControlPage.Envelope) }
    val pageLabels = mapOf(
        SampleControlPage.Envelope to stringResource(Res.string.device_sample_envelope),
        SampleControlPage.Playback to stringResource(Res.string.device_sample_playback),
        SampleControlPage.Loop to stringResource(Res.string.device_sample_loop),
    )
    val pages = if (deviceState.playbackMode == SamplePlaybackMode.GateLoop) {
        SampleControlPage.entries
    } else {
        SampleControlPage.entries.filterNot { it == SampleControlPage.Loop }
    }
    LaunchedEffect(deviceState.playbackMode) {
        if (page == SampleControlPage.Loop && deviceState.playbackMode != SamplePlaybackMode.GateLoop) {
            page = SampleControlPage.Playback
        }
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledSelect(
            label = stringResource(Res.string.device_sample_controls),
            value = pageLabels.getValue(page),
            options = pages.map(pageLabels::getValue),
            modifier = Modifier.width(112.dp),
            onValueChange = { selected -> page = pageLabels.entries.first { it.value == selected }.key },
        )
        when (page) {
            SampleControlPage.Envelope -> EnvelopeStrip(
                state, deviceState, activeDurationMs, volumeMinDb, volumeRangeDb,
                volumeMaxDb, onPushStateChange,
            )
            SampleControlPage.Playback -> PlaybackStrip(state, deviceState, onPushStateChange)
            SampleControlPage.Loop -> LoopStrip(state, deviceState, onPushStateChange)
        }
    }
}

@Composable
private fun EnvelopeStrip(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    activeDurationMs: Float,
    volumeMinDb: Float,
    volumeRangeDb: Float,
    volumeMaxDb: Float,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    var beforeVolume by remember { mutableStateOf(deviceState) }
    AutomatableDial(
        parameterId = "gain",
        type = DialType.Continuous,
        title = stringResource(Res.string.device_sample_gain),
        text = "${if (deviceState.volumeDb >= 0) "+" else ""}${deviceState.volumeDb} dB",
        value = (deviceState.volumeDb - volumeMinDb) / volumeRangeDb,
        defaultValue = 0.5f,
        onStartValueChange = { beforeVolume = state.value },
        onValueChange = { normalized ->
            state.update {
                it.copy(volumeDb = (normalized * volumeRangeDb + volumeMinDb)
                    .coerceIn(volumeMinDb, volumeMaxDb))
            }
        },
        onFinishValueChange = { onPushStateChange(beforeVolume, state.value) },
        onResolveTextValue = { text ->
            text.replace("dB", "").replace("+", "").trim().toFloatOrNull()?.let { value ->
                if (value in volumeMinDb..volumeMaxDb) {
                    updateWithHistory(state, onPushStateChange) { copy(volumeDb = value) }
                }
            }
        },
        isFlat = true,
    )
    FadeDial(
        state = state,
        parameterId = "fadeIn",
        title = stringResource(Res.string.device_sample_fade_in),
        value = deviceState.fadeInMs,
        maximum = activeDurationMs,
        onValueChange = { value -> state.update { it.copy(fadeInMs = value) } },
        onResolveValue = { value ->
            updateWithHistory(state, onPushStateChange) { copy(fadeInMs = value) }
        },
        onFinishValueChange = { before -> onPushStateChange(before, state.value) },
    )
    FadeDial(
        state = state,
        parameterId = "fadeOut",
        title = stringResource(Res.string.device_sample_fade_out),
        value = deviceState.fadeOutMs,
        maximum = activeDurationMs,
        onValueChange = { value -> state.update { it.copy(fadeOutMs = value) } },
        onResolveValue = { value ->
            updateWithHistory(state, onPushStateChange) { copy(fadeOutMs = value) }
        },
        onFinishValueChange = { before -> onPushStateChange(before, state.value) },
    )
}

@Composable
private fun PlaybackStrip(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    var beforePan by remember { mutableStateOf(deviceState) }
    val oneShotLabel = stringResource(Res.string.device_sample_one_shot)
    val gateLoopLabel = stringResource(Res.string.device_sample_gate_loop)
    AutomatableDial(
        parameterId = "pan",
        type = DialType.Continuous,
        title = stringResource(Res.string.device_sample_pan),
        text = formatPan(deviceState.pan, stringResource(Res.string.device_sample_center)),
        value = ((deviceState.pan + 100f) / 200f).coerceIn(0f, 1f),
        defaultValue = 0.5f,
        onStartValueChange = { beforePan = state.value },
        onValueChange = { normalized -> state.update { it.copy(pan = normalized * 200f - 100f) } },
        onFinishValueChange = { onPushStateChange(beforePan, state.value) },
        onResolveTextValue = { text ->
            text.replace("L", "-").replace("R", "").replace("%", "")
                .trim().toFloatOrNull()?.takeIf { it in -100f..100f }?.let { value ->
                    updateWithHistory(state, onPushStateChange) { copy(pan = value) }
                }
        },
        isFlat = true,
    )
    LabeledSelect(
        label = stringResource(Res.string.device_sample_mode),
        value = if (deviceState.playbackMode == SamplePlaybackMode.OneShot) {
            oneShotLabel
        } else gateLoopLabel,
        options = listOf(oneShotLabel, gateLoopLabel),
        modifier = Modifier.width(112.dp),
    ) { selected ->
        updateWithHistory(state, onPushStateChange) {
            copy(
                playbackMode = if (selected == oneShotLabel) {
                    SamplePlaybackMode.OneShot
                } else SamplePlaybackMode.GateLoop,
            )
        }
    }
    LabeledSelect(
        label = stringResource(Res.string.device_sample_choke),
        value = if (deviceState.chokeGroup == 0) stringResource(Res.string.device_sample_off) else deviceState.chokeGroup.toString(),
        options = listOf(stringResource(Res.string.device_sample_off)) + (1..16).map(Int::toString),
        modifier = Modifier.width(82.dp),
    ) { selected ->
        updateWithHistory(state, onPushStateChange) { copy(chokeGroup = selected.toIntOrNull() ?: 0) }
    }
}

@Composable
private fun LoopStrip(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    val customLoop = deviceState.loopStartPosition != null && deviceState.loopEndPosition != null
    val sampleBounds = stringResource(Res.string.device_sample_bounds)
    val custom = stringResource(Res.string.device_sample_custom)
    LabeledSelect(
        label = stringResource(Res.string.device_sample_region),
        value = if (customLoop) custom else sampleBounds,
        options = listOf(sampleBounds, custom),
        modifier = Modifier.width(144.dp),
    ) { selected ->
        updateWithHistory(state, onPushStateChange) {
            if (selected == custom) {
                copy(loopStartPosition = startPosition, loopEndPosition = endPosition)
            } else {
                copy(loopStartPosition = null, loopEndPosition = null)
            }
        }
    }
    val shownStart = deviceState.loopStartPosition ?: deviceState.startPosition
    var beforeStart by remember { mutableStateOf(deviceState) }
    FlatDial(
        type = DialType.Continuous,
        title = stringResource(Res.string.device_sample_loop_start),
        text = "${(shownStart * 100f).roundToInt()}%",
        value = shownStart,
        onStartValueChange = { beforeStart = state.value },
        onValueChange = { value ->
            state.update {
                it.copy(loopStartPosition = value.coerceIn(it.startPosition, (it.loopEndPosition ?: it.endPosition) - 0.001f))
            }
        },
        onFinishValueChange = { onPushStateChange(beforeStart, state.value) },
    )
    val shownEnd = deviceState.loopEndPosition ?: deviceState.endPosition
    var beforeEnd by remember { mutableStateOf(deviceState) }
    FlatDial(
        type = DialType.Continuous,
        title = stringResource(Res.string.device_sample_loop_end),
        text = "${(shownEnd * 100f).roundToInt()}%",
        value = shownEnd,
        onStartValueChange = { beforeEnd = state.value },
        onValueChange = { value ->
            state.update {
                it.copy(loopEndPosition = value.coerceIn((it.loopStartPosition ?: it.startPosition) + 0.001f, it.endPosition))
            }
        },
        onFinishValueChange = { onPushStateChange(beforeEnd, state.value) },
    )
}

@Composable
private fun FadeDial(
    state: MutableStateFlow<SampleChainDeviceState>,
    parameterId: String,
    title: String,
    value: Float,
    maximum: Float,
    onValueChange: (Float) -> Unit,
    onResolveValue: (Float) -> Unit,
    onFinishValueChange: (SampleChainDeviceState) -> Unit,
) {
    var before by remember { mutableStateOf(state.value) }
    AutomatableDial(
        parameterId = parameterId,
        type = DialType.Continuous,
        title = title,
        text = "${value.roundToInt()} ms",
        value = (value / maximum).coerceIn(0f, 1f),
        defaultValue = 0f,
        onStartValueChange = { before = state.value },
        onValueChange = { normalized -> onValueChange(normalized * maximum) },
        onFinishValueChange = { onFinishValueChange(before) },
        onResolveTextValue = { text ->
            text.removeSuffix("ms").trim().toFloatOrNull()
                ?.takeIf { it in 0f..maximum }
                ?.let(onResolveValue)
        },
        isFlat = true,
    )
}

@Composable
private fun LabeledSelect(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Theme[typography][small], color = Theme[colors][mutedForeground])
        Select(value = value, options = options, triggerHeight = 32.dp, onValueChange = onValueChange)
    }
}

private inline fun updateWithHistory(
    state: MutableStateFlow<SampleChainDeviceState>,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
    change: SampleChainDeviceState.() -> SampleChainDeviceState,
) {
    val before = state.value
    state.value = before.change()
    onPushStateChange(before, state.value)
}

private fun formatPan(pan: Float, center: String): String = when {
    pan < -0.5f -> "${(-pan).roundToInt()}L"
    pan > 0.5f -> "${pan.roundToInt()}R"
    else -> center
}
