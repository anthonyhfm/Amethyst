package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsContent
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SampleFlatControlsView(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    volumeMinDb: Float,
    volumeRangeDb: Float,
    volumeMaxDb: Float,
    activeVoiceCount: Int,
    droppedVoiceCount: Long,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onPushStateChange: (before: SampleChainDeviceState, after: SampleChainDeviceState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeDurationMs = (
        deviceState.totalDurationMs * (deviceState.endPosition - deviceState.startPosition)
    ).coerceAtLeast(1f)
    var selectedTab by remember { mutableStateOf("Envelope") }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
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
                onFinishValueChange = {
                    onPushStateChange(state.value.copy(volumeDb = beforeVolume), state.value)
                },
                onResolveTextValue = { text ->
                    text.replace("dB", "").replace("+", "").trim().toFloatOrNull()?.let { value ->
                        if (value in volumeMinDb..volumeMaxDb) {
                            val before = state.value
                            state.update { it.copy(volumeDb = value) }
                            onPushStateChange(before, state.value)
                        }
                    }
                },
            )

            SeparatorBox()

            var beforePan = deviceState.pan
            FlatDial(
                type = DialType.Continuous,
                title = "Pan",
                text = formatPan(deviceState.pan),
                value = ((deviceState.pan + 100f) / 200f).coerceIn(0f, 1f),
                onStartValueChange = { beforePan = deviceState.pan },
                onValueChange = { normalized ->
                    state.update { it.copy(pan = normalized * 200f - 100f) }
                },
                onFinishValueChange = {
                    onPushStateChange(state.value.copy(pan = beforePan), state.value)
                },
                onResolveTextValue = { text ->
                    text.replace("L", "-").replace("R", "").replace("%", "")
                        .trim().toFloatOrNull()?.let { value ->
                            if (value in -100f..100f) {
                                val before = state.value
                                state.update { it.copy(pan = value) }
                                onPushStateChange(before, state.value)
                            }
                        }
                },
            )

            SeparatorBox()

            Column(modifier = Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Mode", style = Theme[typography][small])
                Select(
                    value = deviceState.playbackMode.uiLabel,
                    options = SamplePlaybackMode.entries.map(SamplePlaybackMode::uiLabel),
                    triggerHeight = 32.dp,
                    onValueChange = { label ->
                        val selected = SamplePlaybackMode.entries.first { it.uiLabel == label }
                        val before = state.value
                        state.update { it.copy(playbackMode = selected) }
                        onPushStateChange(before, state.value)
                    },
                )
            }

            Button(
                onClick = onToggleDetails,
                size = ButtonSize.Small,
            ) {
                Text(if (showDetails) "Less" else "More")
            }
        }

        if (showDetails) {
            val tabs = listOf("Envelope", "Playback", "Warp", "Diagnostics")
            Tabs(selectedTab = selectedTab, tabs = tabs) {
            TabsList(modifier = Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    TabsTrigger(
                        key = tab,
                        selected = selectedTab == tab,
                        onSelected = { selectedTab = tab },
                        modifier = Modifier.weight(1f),
                    ) { Text(tab) }
                }
            }

            TabsContent("Envelope") {
                EnvelopeControls(state, deviceState, activeDurationMs, onPushStateChange)
            }
            TabsContent("Playback") {
                PlaybackControls(
                    state = state,
                    deviceState = deviceState,
                    activeVoiceCount = activeVoiceCount,
                    droppedVoiceCount = droppedVoiceCount,
                    onPushStateChange = onPushStateChange,
                )
            }
            TabsContent("Warp") {
                WarpControls(state, deviceState, activeDurationMs, onPushStateChange)
            }
            TabsContent("Diagnostics") {
                SamplingDiagnostics()
            }
            }
        }
    }
}

@Composable
private fun SamplingDiagnostics() {
    val diagnostics by produceState(initialValue = WorkspaceRepository.samplingChain.diagnosticsSnapshot()) {
        while (true) {
            value = WorkspaceRepository.samplingChain.diagnosticsSnapshot()
            delay(250)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Active voices: ${diagnostics.activeVoices}", style = Theme[typography][small])
        Text("Voice drops: ${diagnostics.voiceDrops}", style = Theme[typography][small])
        Text("Command queue drops: ${diagnostics.commandQueueDrops}", style = Theme[typography][small])
        Text("Render overruns: ${diagnostics.render.renderOverruns}", style = Theme[typography][small])
        Text("Sanitized samples: ${diagnostics.render.sanitizedSamples}", style = Theme[typography][small])
        Text(
            "DSP load: ${diagnostics.render.lastDspLoadPercent.roundToInt()}% · peak ${diagnostics.render.peakDspLoadPercent.roundToInt()}%",
            style = Theme[typography][small],
        )
        Text(
            "Graph: ${diagnostics.graphLatencyFrames} frames latency · ${diagnostics.graphTailFrames} frames tail",
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
        diagnostics.graphDiagnostics.forEach { message ->
            Text("Graph warning: $message", style = Theme[typography][small], color = Theme[colors][mutedForeground])
        }
    }
}

@Composable
private fun WarpControls(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    activeDurationMs: Float,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    val workspaceBpm by WorkspaceRepository.bpm.collectAsState()
    val ratio = sampleTempoRatio(deviceState.warpMode, deviceState.sourceBpm, workspaceBpm)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledSelect(
                label = "Tempo Mode",
                value = deviceState.warpMode.uiLabel,
                options = SampleWarpMode.entries.map(SampleWarpMode::uiLabel),
                modifier = Modifier.width(132.dp),
            ) { selected ->
                val before = state.value
                state.update { current ->
                    current.copy(warpMode = SampleWarpMode.entries.first { it.uiLabel == selected })
                }
                onPushStateChange(before, state.value)
            }

            var beforeSourceBpm = deviceState.sourceBpm
            val shownBpm = deviceState.sourceBpm ?: 120f
            FlatDial(
                type = DialType.Continuous,
                title = "Source BPM",
                text = deviceState.sourceBpm?.let { "${it.roundToInt()} BPM" } ?: "Set BPM",
                value = ((shownBpm - SOURCE_BPM_MIN) / (SOURCE_BPM_MAX - SOURCE_BPM_MIN)).coerceIn(0f, 1f),
                onStartValueChange = { beforeSourceBpm = deviceState.sourceBpm },
                onValueChange = { normalized ->
                    state.update {
                        it.copy(sourceBpm = SOURCE_BPM_MIN + normalized * (SOURCE_BPM_MAX - SOURCE_BPM_MIN))
                    }
                },
                onFinishValueChange = {
                    onPushStateChange(state.value.copy(sourceBpm = beforeSourceBpm), state.value)
                },
                onResolveTextValue = { text ->
                    text.removeSuffix("BPM").trim().toFloatOrNull()?.let { bpm ->
                        if (bpm in SOURCE_BPM_MIN..SOURCE_BPM_MAX) {
                            val before = state.value
                            state.update { it.copy(sourceBpm = bpm) }
                            onPushStateChange(before, state.value)
                        }
                    }
                },
            )
        }

        if (deviceState.warpMode != SampleWarpMode.Off && deviceState.sourceBpm == null) {
            Text(
                "Set Source BPM to make this sample follow the workspace tempo.",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        } else {
            val sourceLabel = deviceState.sourceBpm?.roundToInt()?.toString() ?: "—"
            val resultSeconds = activeDurationMs / 1_000.0 / ratio
            Text(
                "$sourceLabel → ${workspaceBpm.roundToInt()} BPM · ${formatRatio(ratio)}× · ${formatSeconds(resultSeconds)} s",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }
        if (deviceState.warpMode == SampleWarpMode.Warp) {
            Text(
                "Pitch lock · 128-frame latency · realtime quality",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }
    }
}

@Composable
private fun EnvelopeControls(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    activeDurationMs: Float,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var beforeFadeIn = deviceState.fadeInMs
        FlatDial(
            type = DialType.Continuous,
            title = "Fade In",
            text = "${deviceState.fadeInMs.roundToInt()} ms",
            value = (deviceState.fadeInMs / activeDurationMs).coerceIn(0f, 1f),
            onStartValueChange = { beforeFadeIn = deviceState.fadeInMs },
            onValueChange = { value -> state.update { it.copy(fadeInMs = value * activeDurationMs) } },
            onFinishValueChange = {
                onPushStateChange(state.value.copy(fadeInMs = beforeFadeIn), state.value)
            },
            onResolveTextValue = { text ->
                text.removeSuffix("ms").trim().toFloatOrNull()?.let { value ->
                    if (value in 0f..activeDurationMs) {
                        val before = state.value
                        state.update { it.copy(fadeInMs = value) }
                        onPushStateChange(before, state.value)
                    }
                }
            },
        )
        SeparatorBox()
        var beforeFadeOut = deviceState.fadeOutMs
        FlatDial(
            type = DialType.Continuous,
            title = "Fade Out",
            text = "${deviceState.fadeOutMs.roundToInt()} ms",
            value = (deviceState.fadeOutMs / activeDurationMs).coerceIn(0f, 1f),
            onStartValueChange = { beforeFadeOut = deviceState.fadeOutMs },
            onValueChange = { value -> state.update { it.copy(fadeOutMs = value * activeDurationMs) } },
            onFinishValueChange = {
                onPushStateChange(state.value.copy(fadeOutMs = beforeFadeOut), state.value)
            },
            onResolveTextValue = { text ->
                text.removeSuffix("ms").trim().toFloatOrNull()?.let { value ->
                    if (value in 0f..activeDurationMs) {
                        val before = state.value
                        state.update { it.copy(fadeOutMs = value) }
                        onPushStateChange(before, state.value)
                    }
                }
            },
        )
    }
}

@Composable
private fun PlaybackControls(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    activeVoiceCount: Int,
    droppedVoiceCount: Long,
    onPushStateChange: (SampleChainDeviceState, SampleChainDeviceState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledSelect(
                label = "Choke Group",
                value = if (deviceState.chokeGroup == 0) "Off" else deviceState.chokeGroup.toString(),
                options = listOf("Off") + (1..16).map(Int::toString),
                modifier = Modifier.width(128.dp),
            ) { selected ->
                val before = state.value
                state.update { it.copy(chokeGroup = selected.toIntOrNull() ?: 0) }
                onPushStateChange(before, state.value)
            }

            if (deviceState.playbackMode == SamplePlaybackMode.GateLoop) {
                LabeledSelect(
                    label = "Loop Region",
                    value = if (deviceState.loopStartPosition == null) "Sample Bounds" else "Custom",
                    options = listOf("Sample Bounds", "Custom"),
                    modifier = Modifier.width(144.dp),
                ) { selected ->
                    val before = state.value
                    state.update {
                        if (selected == "Custom") {
                            it.copy(
                                loopStartPosition = it.startPosition,
                                loopEndPosition = it.endPosition,
                            )
                        } else {
                            it.copy(loopStartPosition = null, loopEndPosition = null)
                        }
                    }
                    onPushStateChange(before, state.value)
                }
            }
        }

        if (deviceState.playbackMode == SamplePlaybackMode.GateLoop) {
            Text(
                "Loops while the triggering pad is held",
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }
        if (
            deviceState.playbackMode == SamplePlaybackMode.GateLoop &&
            deviceState.loopStartPosition != null &&
            deviceState.loopEndPosition != null
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var beforeLoopStart = deviceState.loopStartPosition
                FlatDial(
                    type = DialType.Continuous,
                    title = "Loop Start",
                    text = "${(deviceState.loopStartPosition * 100f).roundToInt()}%",
                    value = deviceState.loopStartPosition,
                    onStartValueChange = { beforeLoopStart = deviceState.loopStartPosition },
                    onValueChange = { value ->
                        state.update {
                            it.copy(
                                loopStartPosition = value.coerceIn(
                                    it.startPosition,
                                    (it.loopEndPosition ?: it.endPosition) - 0.001f,
                                ),
                            )
                        }
                    },
                    onFinishValueChange = {
                        onPushStateChange(
                            state.value.copy(loopStartPosition = beforeLoopStart),
                            state.value,
                        )
                    },
                )
                SeparatorBox()
                var beforeLoopEnd = deviceState.loopEndPosition
                FlatDial(
                    type = DialType.Continuous,
                    title = "Loop End",
                    text = "${(deviceState.loopEndPosition * 100f).roundToInt()}%",
                    value = deviceState.loopEndPosition,
                    onStartValueChange = { beforeLoopEnd = deviceState.loopEndPosition },
                    onValueChange = { value ->
                        state.update {
                            it.copy(
                                loopEndPosition = value.coerceIn(
                                    (it.loopStartPosition ?: it.startPosition) + 0.001f,
                                    it.endPosition,
                                ),
                            )
                        }
                    },
                    onFinishValueChange = {
                        onPushStateChange(
                            state.value.copy(loopEndPosition = beforeLoopEnd),
                            state.value,
                        )
                    },
                )
            }
        }
        val diagnostics = buildString {
            append("Voices: ").append(activeVoiceCount)
            if (droppedVoiceCount > 0) append(" · Voice steals/dropped triggers: ").append(droppedVoiceCount)
        }
        Text(diagnostics, style = Theme[typography][small], color = Theme[colors][mutedForeground])
    }
}

@Composable
private fun LabeledSelect(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Theme[typography][small])
        Select(
            value = value,
            options = options,
            triggerHeight = 32.dp,
            onValueChange = onValueChange,
        )
    }
}

@Composable
private fun SeparatorBox() {
    Box(modifier = Modifier.height(32.dp)) {
        Separator(orientation = SeparatorOrientation.Vertical)
    }
}

private val SamplePlaybackMode.uiLabel: String
    get() = when (this) {
        SamplePlaybackMode.OneShot -> "One Shot"
        SamplePlaybackMode.GateLoop -> "Gate Loop"
    }

private val SampleWarpMode.uiLabel: String
    get() = when (this) {
        SampleWarpMode.Off -> "Off"
        SampleWarpMode.Repitch -> "Repitch"
        SampleWarpMode.Warp -> "Warp"
    }

private const val SOURCE_BPM_MIN = 20f
private const val SOURCE_BPM_MAX = 300f

private fun formatRatio(value: Double): String = ((value * 100.0).roundToInt() / 100.0).toString()
private fun formatSeconds(value: Double): String = ((value * 10.0).roundToInt() / 10.0).toString()

private fun formatPan(pan: Float): String = when {
    pan < -0.5f -> "${(-pan).roundToInt()}L"
    pan > 0.5f -> "${pan.roundToInt()}R"
    else -> "Center"
}
