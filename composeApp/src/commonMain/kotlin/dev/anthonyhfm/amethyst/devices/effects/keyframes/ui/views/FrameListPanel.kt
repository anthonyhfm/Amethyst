package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.FrameCreationButton
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.FramePreviewButton
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.secondary

@Composable
fun ColumnScope.FrameListPanel(
    state: KeyframesChainDeviceContract.KeyframesChainDeviceState,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit,
    parent: dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice? = null
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderState<KeyframesChainDeviceContract.Frame>()

    ReorderContainer(
        state = reorderState,
        modifier = Modifier.weight(1f),
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .clip(DefaultShape)
                .fillMaxHeight()
                .width(220.dp)
                .background(Theme[colors][card])
                .border(1.dp, Theme[colors][border], DefaultShape)
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp)
        ) {
            itemsIndexed(state.frames, key = { _, frame -> frame._internalUuid }) { index, frame ->
                FrameCreationButton(
                    onCreateFrame = {
                        onEvent(
                            KeyframesChainDeviceContract.Event.OnAddFrame(atIndex = index)
                        )
                    }
                )

                ReorderableItem(
                    state = reorderState,
                    key = frame._internalUuid,
                    data = frame,
                    enabled = state.frames.size > 1,
                    useDragAnchor = true,
                    onDragEnter = { draggedState ->
                        val fromIndex = state.frames.indexOfFirst { it._internalUuid == draggedState.data._internalUuid }
                        if (fromIndex != -1 && fromIndex != index) {
                            onEvent(KeyframesChainDeviceContract.Event.OnChangeFramePosition(fromIndex, index))
                        }
                    },
                ) {
                    FramePreviewButton(
                        index = index,
                        selected = state.currentFrameIndex == index,
                        timing = frame.timing,
                        onEvent = onEvent,
                        parent = parent,
                    )
                }
            }

            item {
                FrameCreationButton(
                    expanded = true,
                    onCreateFrame = {
                        onEvent(KeyframesChainDeviceContract.Event.OnAddFrame())
                    }
                )
            }
        }
    }
}