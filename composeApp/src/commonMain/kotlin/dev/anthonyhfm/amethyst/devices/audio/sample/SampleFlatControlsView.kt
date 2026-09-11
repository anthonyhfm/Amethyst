package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

private enum class SampleControlPage(val label: String) {
    Envelope("Envelope"), Playback("Playback"), Loop("Loop")
}

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

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabeledSelect(
            label = "Controls",
            value = page.label,
            options = SampleControlPage.entries.map(SampleControlPage::label),
            modifier = Modifier.width(112.dp),
            onValueChange = { selected -> page = SampleControlPage.entries.first { it.label == selected } },
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
    var beforeVolume = deviceState.volumeDb
    FlatDial(
        type = DialType.Continuous,
        title = "Gain",
        text = "${if (deviceState.volumeDb >= 0) "+" else ""}${deviceState.volumeDb} dB",
        value = (deviceState.volumeDb - volumeMinDb) / volumeRangeDb,
        onStartValueChange = { beforeVolume = deviceState.volumeDb },
        onValueChange = { normalized ->
            state.update {
                it.copy(volumeDb = (normalized * volumeRangeDb + volumeMinDb)
                    .coerceIn(volumeMinDb, volumeMaxDb))
            }
        },
        onFinishValueChange = { onPushStateChange(state.value.copy(volumeDb = beforeVolume), state.value) },
        onResolveTextValue = { text ->
            text.replace("dB", "").replace("+", "").trim().toFloatOrNull()?.let { value ->
                if (value in volumeMinDb..volumeMaxDb) {
                    updateWithHistory(state, onPushStateChange) { copy(volumeDb = value) }
                }
            }
        },
    )
    FadeDial(
        title = "Fade In",
        value = deviceState.fadeInMs,
        maximum = activeDurationMs,
        onValueChange = { value -> state.update { it.copy(fadeInMs = value) } },
        onFinishValueChange = { before -> onPushStateChange(state.value.copy(fadeInMs = before), state.value) },
    )
    FadeDial(
        title = "Fade Out",
        value = deviceState.fadeOutMs,
        maximum = activeDurationMs,
        onValueChange = { value -> state.update { it.copy(fadeOutMs = value) } },
        onFinishValueChange = { before -> onPushStateChange(state.value.copy(fadeOutMs = before), state.value) },
    )
}

@Composable
private fun PlaybackStrip(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    var beforePan = deviceState.pan
    FlatDial(
        type = DialType.Continuous,
        title = "Pan",
        text = formatPan(deviceState.pan),
        value = ((deviceState.pan + 100f) / 200f).coerceIn(0f, 1f),
        onStartValueChange = { beforePan = deviceState.pan },
        onValueChange = { normalized -> state.update { it.copy(pan = normalized * 200f - 100f) } },
        onFinishValueChange = { onPushStateChange(state.value.copy(pan = beforePan), state.value) },
        onResolveTextValue = { text ->
            text.replace("L", "-").replace("R", "").replace("%", "")
                .trim().toFloatOrNull()?.takeIf { it in -100f..100f }?.let { value ->
                    updateWithHistory(state, onPushStateChange) { copy(pan = value) }
                }
        },
    )
    LabeledSelect(
        label = "Mode",
        value = deviceState.playbackMode.uiLabel,
        options = SamplePlaybackMode.entries.map(SamplePlaybackMode::uiLabel),
        modifier = Modifier.width(112.dp),
    ) { selected ->
        updateWithHistory(state, onPushStateChange) {
            copy(playbackMode = SamplePlaybackMode.entries.first { it.uiLabel == selected })
        }
    }
    LabeledSelect(
        label = "Choke",
        value = if (deviceState.chokeGroup == 0) "Off" else deviceState.chokeGroup.toString(),
        options = listOf("Off") + (1..16).map(Int::toString),
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
    LabeledSelect(
        label = "Region",
        value = if (customLoop) "Custom" else "Sample Bounds",
        options = listOf("Sample Bounds", "Custom"),
        modifier = Modifier.width(144.dp),
    ) { selected ->
        updateWithHistory(state, onPushStateChange) {
            if (selected == "Custom") {
                copy(loopStartPosition = startPosition, loopEndPosition = endPosition)
            } else {
                copy(loopStartPosition = null, loopEndPosition = null)
            }
        }
    }
    val shownStart = deviceState.loopStartPosition ?: deviceState.startPosition
    var beforeStart = deviceState.loopStartPosition
    FlatDial(
        type = DialType.Continuous,
        title = "Loop Start",
        text = "${(shownStart * 100f).roundToInt()}%",
        value = shownStart,
        onStartValueChange = { beforeStart = deviceState.loopStartPosition },
        onValueChange = { value ->
            state.update {
                it.copy(loopStartPosition = value.coerceIn(it.startPosition, (it.loopEndPosition ?: it.endPosition) - 0.001f))
            }
        },
        onFinishValueChange = { onPushStateChange(state.value.copy(loopStartPosition = beforeStart), state.value) },
    )
    val shownEnd = deviceState.loopEndPosition ?: deviceState.endPosition
    var beforeEnd = deviceState.loopEndPosition
    FlatDial(
        type = DialType.Continuous,
        title = "Loop End",
        text = "${(shownEnd * 100f).roundToInt()}%",
        value = shownEnd,
        onStartValueChange = { beforeEnd = deviceState.loopEndPosition },
        onValueChange = { value ->
            state.update {
                it.copy(loopEndPosition = value.coerceIn((it.loopStartPosition ?: it.startPosition) + 0.001f, it.endPosition))
            }
        },
        onFinishValueChange = { onPushStateChange(state.value.copy(loopEndPosition = beforeEnd), state.value) },
    )
}

@Composable
private fun FadeDial(
    title: String,
    value: Float,
    maximum: Float,
    onValueChange: (Float) -> Unit,
    onFinishValueChange: (Float) -> Unit,
) {
    var before = value
    FlatDial(
        type = DialType.Continuous,
        title = title,
        text = "${value.roundToInt()} ms",
        value = (value / maximum).coerceIn(0f, 1f),
        onStartValueChange = { before = value },
        onValueChange = { normalized -> onValueChange(normalized * maximum) },
        onFinishValueChange = { onFinishValueChange(before) },
        onResolveTextValue = { text ->
            text.removeSuffix("ms").trim().toFloatOrNull()
                ?.takeIf { it in 0f..maximum }
                ?.let(onValueChange)
        },
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

private val SamplePlaybackMode.uiLabel: String
    get() = when (this) {
        SamplePlaybackMode.OneShot -> "One Shot"
        SamplePlaybackMode.GateLoop -> "Gate Loop"
    }

private fun formatPan(pan: Float): String = when {
    pan < -0.5f -> "${(-pan).roundToInt()}L"
    pan > 0.5f -> "${pan.roundToInt()}R"
    else -> "Center"
}
