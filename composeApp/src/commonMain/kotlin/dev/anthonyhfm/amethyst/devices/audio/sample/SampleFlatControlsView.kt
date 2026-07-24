package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

@Composable
fun SampleFlatControlsView(
    state: MutableStateFlow<SampleChainDeviceState>,
    deviceState: SampleChainDeviceState,
    volumeMinDb: Float,
    volumeRangeDb: Float,
    volumeMaxDb: Float,
    onPushStateChange: (before: SampleChainDeviceState, after: SampleChainDeviceState) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeDurationMs = (deviceState.totalDurationMs * (deviceState.endPosition - deviceState.startPosition)).coerceAtLeast(1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var beforeFadeIn = deviceState.fadeInMs
        FlatDial(
            type = DialType.Continuous,
            title = "Fade In",
            text = "${deviceState.fadeInMs.roundToInt()} ms",
            value = (deviceState.fadeInMs / activeDurationMs).coerceIn(0f, 1f),
            onStartValueChange = { beforeFadeIn = deviceState.fadeInMs },
            onValueChange = { value ->
                state.update {
                    it.copy(fadeInMs = (value * activeDurationMs).coerceIn(0f, activeDurationMs))
                }
            },
            onFinishValueChange = {
                onPushStateChange(
                    state.value.copy(fadeInMs = beforeFadeIn),
                    state.value
                )
            },
            onResolveTextValue = { text ->
                val ms = text.removeSuffix("ms").trim().toIntOrNull()
                ms?.let { v ->
                    if (v in 0..activeDurationMs.toInt()) {
                        val before = state.value
                        state.update { it.copy(fadeInMs = v.toFloat()) }
                        onPushStateChange(before, state.value)
                    }
                }
            }
        )

        Box(modifier = Modifier.height(32.dp)) {
            Separator(orientation = SeparatorOrientation.Vertical)
        }

        var beforeFadeOut = deviceState.fadeOutMs
        FlatDial(
            type = DialType.Continuous,
            title = "Fade Out",
            text = "${deviceState.fadeOutMs.roundToInt()} ms",
            value = (deviceState.fadeOutMs / activeDurationMs).coerceIn(0f, 1f),
            onStartValueChange = { beforeFadeOut = deviceState.fadeOutMs },
            onValueChange = { value ->
                state.update {
                    it.copy(fadeOutMs = (value * activeDurationMs).coerceIn(0f, activeDurationMs))
                }
            },
            onFinishValueChange = {
                onPushStateChange(
                    state.value.copy(fadeOutMs = beforeFadeOut),
                    state.value
                )
            },
            onResolveTextValue = { text ->
                val ms = text.removeSuffix("ms").trim().toIntOrNull()
                ms?.let { v ->
                    if (v in 0..activeDurationMs.toInt()) {
                        val before = state.value
                        state.update { it.copy(fadeOutMs = v.toFloat()) }
                        onPushStateChange(before, state.value)
                    }
                }
            }
        )

        Box(modifier = Modifier.height(32.dp)) {
            Separator(orientation = SeparatorOrientation.Vertical)
        }

        var beforeVolume = deviceState.volumeDb
        FlatDial(
            type = DialType.Continuous,
            title = "Volume",
            text = "${if (deviceState.volumeDb >= 0) "+" else ""}${deviceState.volumeDb} dB",
            value = (deviceState.volumeDb - volumeMinDb) / volumeRangeDb,
            onStartValueChange = { beforeVolume = deviceState.volumeDb },
            onValueChange = { value ->
                state.update {
                    it.copy(
                        volumeDb = ((value * volumeRangeDb) + volumeMinDb).coerceIn(
                            volumeMinDb,
                            volumeMaxDb
                        )
                    )
                }
            },
            onFinishValueChange = {
                onPushStateChange(
                    state.value.copy(volumeDb = beforeVolume),
                    state.value
                )
            },
            onResolveTextValue = { text ->
                val db = text.replace("dB", "").replace("+", "").trim().toFloatOrNull()
                db?.let { v ->
                    if (v in volumeMinDb..volumeMaxDb) {
                        val before = state.value
                        state.update { it.copy(volumeDb = v) }
                        onPushStateChange(before, state.value)
                    }
                }
            }
        )
    }
}
