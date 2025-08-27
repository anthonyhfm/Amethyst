package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.FrameCreationButton
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.FramePreviewButton
import dev.anthonyhfm.amethyst.ui.modifier.EditorEvent
import dev.anthonyhfm.amethyst.ui.modifier.editorEventListener
import kotlinx.coroutines.flow.update
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ColumnScope.FrameListPanel(
    state: KeyframesChainDeviceContract.KeyframesChainDeviceState,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit,
    parent: dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice? = null
) {
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .weight(1f)
            .fillMaxHeight()
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp)
    ) {
        itemsIndexed(state.frames, key = { _, frame -> frame }) { index, frame ->
            FrameCreationButton(
                onCreateFrame = {
                    onEvent(
                        KeyframesChainDeviceContract.Event.OnAddFrame(
                            atIndex = index,
                        )
                    )
                }
            )

            FramePreviewButton(
                index = index,
                selected = state.currentFrameIndex == index,
                timing = frame.timing,
                onEvent = onEvent,
                parent = parent
            )
        }

        item {
            FrameCreationButton(
                expanded = true,
                onCreateFrame = {
                    onEvent(
                        KeyframesChainDeviceContract.Event.OnAddFrame()
                    )
                }
            )
        }
    }
}