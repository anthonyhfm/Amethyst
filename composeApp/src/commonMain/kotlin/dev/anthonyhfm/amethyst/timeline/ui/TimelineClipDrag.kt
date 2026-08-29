package dev.anthonyhfm.amethyst.timeline.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.TimelineClipMoveEngine
import dev.anthonyhfm.amethyst.timeline.TimelineEditCommand
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState

data class TimelineClipDragCallbacks(
    val onStart: (Offset) -> Unit,
    val onDrag: (Offset) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

@Stable
class TimelineClipDragCoordinator {
    data class Session(
        val anchorKey: TimelineClipKey,
        val entryKeys: List<TimelineClipKey>,
        val pointerStartInRoot: Offset,
        val initialScrollX: Float,
    )

    var session by mutableStateOf<Session?>(null)
        private set
    var pointerInRoot by mutableStateOf(Offset.Unspecified)
        private set
    var surfaceBoundsInRoot by mutableStateOf(Rect.Zero)
        private set
    val laneBoundsInRoot = mutableStateMapOf<Int, Rect>()

    val isActive: Boolean get() = session != null

    fun updateSurfaceBounds(bounds: Rect) {
        surfaceBoundsInRoot = bounds
    }

    fun updateLaneBounds(trackIndex: Int, bounds: Rect) {
        laneBoundsInRoot[trackIndex] = bounds
    }

    fun begin(
        anchorKey: TimelineClipKey,
        pointerRoot: Offset,
        viewport: EditorViewportState,
        tracks: List<TimelineTrack<*>>,
    ) {
        val anchorTrack = tracks.getOrNull(anchorKey.trackIndex) ?: return
        val anchorExists = when (anchorTrack) {
            is AudioTimelineTrack -> anchorTrack.entries.containsKey(anchorKey.entryStartMs)
            is MidiTimelineTrack -> anchorTrack.entries.containsKey(anchorKey.entryStartMs)
            else -> false
        }
        if (!anchorExists) return

        val selectedKeys = SelectionManager.selections.value
            .filterIsInstance<Selectable.TimelineEntryItem>()
            .filter { it.clipId == null }
            .map { TimelineClipKey(it.trackIndex, it.entryStartMs) }
        val anchorWasSelected = anchorKey in selectedKeys
        if (!anchorWasSelected) {
            SelectionManager.select(Selectable.TimelineEntryItem(anchorKey.trackIndex, anchorKey.entryStartMs))
        }
        val sameKindKeys = (if (anchorWasSelected) selectedKeys else listOf(anchorKey)).filter { key ->
            tracks.getOrNull(key.trackIndex)?.let { it::class == anchorTrack::class } == true
        }.distinct().ifEmpty { listOf(anchorKey) }

        session = Session(anchorKey, sameKindKeys, pointerRoot, viewport.scrollX)
        pointerInRoot = pointerRoot
    }

    fun updatePointer(pointerRoot: Offset) {
        if (session != null) pointerInRoot = pointerRoot
    }

    fun buildCommand(
        viewport: EditorViewportState,
        bpm: Double,
        gridType: GridUtils.GridType,
        snapEnabled: Boolean,
    ): TimelineEditCommand.MoveEntries? {
        val active = session ?: return null
        if (pointerInRoot.x.isNaN() || pointerInRoot.y.isNaN()) return null
        val targetTrackIndex = laneBoundsInRoot.entries
            .firstOrNull { (_, bounds) -> bounds.contains(pointerInRoot) }
            ?.key ?: return null
        val anchorTrackStart = TimelineRepositoryAccessor.entryStart(active.anchorKey) ?: return null
        val deltaPx = pointerInRoot.x - active.pointerStartInRoot.x + viewport.scrollX - active.initialScrollX
        val rawStart = (anchorTrackStart + deltaPx / viewport.zoomX).toLong().coerceAtLeast(0L)
        val targetStart = if (snapEnabled) {
            GridUtils.snapToGrid(rawStart, viewport.zoomX, bpm, gridType)
        } else {
            rawStart
        }
        return TimelineEditCommand.MoveEntries(
            entryKeys = active.entryKeys,
            anchorKey = active.anchorKey,
            targetTrackIndex = targetTrackIndex,
            targetStartMs = targetStart,
        )
    }

    fun preview(
        viewport: EditorViewportState,
        bpm: Double,
        gridType: GridUtils.GridType,
        snapEnabled: Boolean,
    ): TimelineClipMoveEngine.Preview = buildCommand(viewport, bpm, gridType, snapEnabled)
        ?.let(TimelineClipMoveEngine::preview)
        ?: TimelineClipMoveEngine.Preview(false)

    fun finish() {
        session = null
        pointerInRoot = Offset.Unspecified
    }

    private object TimelineRepositoryAccessor {
        fun entryStart(key: TimelineClipKey): Long? {
            val track = dev.anthonyhfm.amethyst.timeline.TimelineRepository.tracks.value.getOrNull(key.trackIndex)
            return when (track) {
                is AudioTimelineTrack -> track.entries[key.entryStartMs]?.startTimeMs
                is MidiTimelineTrack -> track.entries[key.entryStartMs]?.startTimeMs
                else -> null
            }
        }
    }
}
