package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.timelineTrackRows
import dev.anthonyhfm.amethyst.timeline.ui.components.AddTrackButton
import dev.anthonyhfm.amethyst.timeline.ui.components.track.TrackInfo
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme

@Composable
fun TrackListView(
    tracks: List<TimelineTrack<*>>,
    verticalScrollState: ScrollState = rememberScrollState(),
    onTrackVolumeChange: (trackIndex: Int, value: Float) -> Unit = { _, _ -> },
    onTrackSoloToggle: (trackIndex: Int) -> Unit = {},
    onTrackMuteToggle: (trackIndex: Int) -> Unit = {},
    onTrackReorder: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onAddLightsTrack: () -> Unit = {},
    onAddAudioTrack: () -> Unit = {},
) {
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val reorderState = rememberReorderState<TimelineTrack<*>>()

    Box(
        modifier = Modifier
            .width(timelineDimensions.trackHeaderWidth)
            .fillMaxHeight()
            .zIndex(10f)
            .background(timelinePalette.laneSurfaceRaised)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = timelinePalette.shellBorder,
                    start = Offset(size.width - stroke / 2f, 0f),
                    end = Offset(size.width - stroke / 2f, size.height),
                    strokeWidth = stroke
                )
            },
    ) {
        ReorderContainer(
            state = reorderState,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            SelectionManager.clear()
                        }
                    }
                    .padding(horizontal = 6.dp)
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(timelineDimensions.laneSpacing),
            ) {
                tracks.timelineTrackRows().forEach { trackRow ->
                    ReorderableItem(
                        state = reorderState,
                        key = trackRow.track.trackId,
                        data = trackRow.track,
                        enabled = tracks.size > 1,
                        useDragAnchor = true,
                        onDragEnter = { draggedState ->
                            val fromIndex = tracks.indexOfFirst { it.trackId == draggedState.data.trackId }
                            val toIndex = trackRow.trackIndex
                            if (fromIndex != -1 && fromIndex != toIndex) {
                                onTrackReorder(fromIndex, toIndex)
                            }
                        },
                    ) {
                        TrackInfo(
                            track = trackRow.track,
                            allTracks = tracks,
                            trackIndex = trackRow.trackIndex,
                            nestingLevel = trackRow.nestingLevel,
                            onTrackVolumeChange = onTrackVolumeChange,
                            onTrackSoloToggle = onTrackSoloToggle,
                            onTrackMuteToggle = onTrackMuteToggle,
                            modifier = Modifier.dragAnchor(),
                        )
                    }
                }

                if (tracks.isNotEmpty()) {
                    Separator(
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                AddTrackButton(
                    onAddLightsTrack = onAddLightsTrack,
                    onAddAudioTrack = onAddAudioTrack,
                )
            }
        }

        Separator(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            orientation = SeparatorOrientation.Vertical
        )
    }
}
