package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.ColorControls
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.RecentColorsRow
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.TimingControls
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun BoxScope.FrameDrawingPanel(
    state: KeyframesChainDeviceContract.KeyframesChainDeviceState,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit
) {
    val selections by SelectionManager.selections.collectAsState()
    val selectedKeyframes = selections.filterIsInstance<Selectable.KeyframeItem>()
    val recentColors by WorkspaceRepository.recentColors.collectAsState()

    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .fillMaxHeight()
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        ColorControls(
            color = state.selectedColor.let { Color(it.first, it.second, it.third) },
            onColorChange = {
                onEvent(
                    KeyframesChainDeviceContract.Event.OnColorUpdate(it)
                )
            }
        )

        Spacer(Modifier.height(32.dp))

        RecentColorsRow(
            colors = recentColors,
            selected = state.selectedColor,
            onPick = { color ->
                onEvent(KeyframesChainDeviceContract.Event.OnColorUpdate(color))
            }
        )

        Spacer(Modifier.weight(1f))

        TimingControls(
            timing = state.frames[state.currentFrameIndex].timing,
            onTimingChanged = { timing ->
                if (selectedKeyframes.size > 1) {
                    onEvent(
                        KeyframesChainDeviceContract.Event.OnChangeMultiFrameTiming(
                            frameIndices = selectedKeyframes.map { it.frameIndex },
                            timing = timing,
                            gate = state.frames[state.currentFrameIndex].gate
                        )
                    )
                } else {
                    onEvent(
                        KeyframesChainDeviceContract.Event.OnChangeFrameTiming(
                            frameIndex = state.currentFrameIndex,
                            timing = timing,
                            gate = state.frames[state.currentFrameIndex].gate
                        )
                    )
                }
            },
            gate = state.frames[state.currentFrameIndex].gate,
            onGateChanged = { gate ->
                if (selectedKeyframes.size > 1) {
                    onEvent(
                        KeyframesChainDeviceContract.Event.OnChangeMultiFrameTiming(
                            frameIndices = selectedKeyframes.map { it.frameIndex },
                            timing = state.frames[state.currentFrameIndex].timing,
                            gate = gate
                        )
                    )
                } else {
                    onEvent(
                        KeyframesChainDeviceContract.Event.OnChangeFrameTiming(
                            frameIndex = state.currentFrameIndex,
                            timing = state.frames[state.currentFrameIndex].timing,
                            gate = gate
                        )
                    )
                }
            }
        )
    }
}