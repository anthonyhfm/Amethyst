package dev.anthonyhfm.amethyst.core.controls.selection

import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.adjacentTimelineTimeSelection
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils.GridIntervals

internal data class TimelineSelectionPoint(val trackIndex: Int, val timeMs: Long)

internal data class TimelineSelectionArea(
    val anchor: TimelineSelectionPoint,
    val focus: TimelineSelectionPoint,
)

internal object TimelineAreaSelection {
    private var area: TimelineSelectionArea? = null
    private var projection = emptyList<Selectable>()
    private var trackIds = emptyList<String>()

    fun current(): TimelineSelectionArea? {
        val selections = SelectionManager.selections.value
        val tracks = TimelineRepository.tracks.value
        val tracksMatch = projection.all { selection ->
            val index = when (selection) {
                is Selectable.TimelineTime -> selection.trackIndex
                is Selectable.TimelineRange -> selection.trackIndex
                else -> -1
            }
            tracks.getOrNull(index = index)?.trackId == trackIds.getOrNull(index = index)
        }
        if (area != null && selections == projection && tracksMatch) {
            return area
        }
        if (area != null && selections == projection && !tracksMatch) {
            area = null
            SelectionManager.clear()
            return null
        }
        area = null
        val ranges = selections.filterIsInstance<Selectable.TimelineRange>()
        val times = selections.filterIsInstance<Selectable.TimelineTime>()
        val restored = when {
            ranges.isNotEmpty() && ranges.size == selections.size -> {
                val first = ranges.first()
                val last = ranges.last()
                TimelineSelectionArea(
                    anchor = TimelineSelectionPoint(trackIndex = first.trackIndex, timeMs = first.anchorTimeMs),
                    focus = TimelineSelectionPoint(
                        trackIndex = last.trackIndex,
                        timeMs = if (last.anchorTimeMs == last.startMs) last.endMs else last.startMs,
                    ),
                )
            }
            times.isNotEmpty() && times.size == selections.size -> TimelineSelectionArea(
                anchor = TimelineSelectionPoint(trackIndex = times.first().trackIndex, timeMs = times.first().timeMs),
                focus = TimelineSelectionPoint(trackIndex = times.last().trackIndex, timeMs = times.last().timeMs),
            )
            else -> null
        }
        return restored?.takeIf { it.anchor.trackIndex in tracks.indices && it.focus.trackIndex in tracks.indices }
    }

    fun select(trackIndex: Int, timeMs: Long, extend: Boolean) {
        if (trackIndex !in TimelineRepository.tracks.value.indices) {
            return
        }
        val point = TimelineSelectionPoint(trackIndex = trackIndex, timeMs = timeMs.coerceAtLeast(minimumValue = 0L))
        val previous = if (extend) current() else null
        publish(value = TimelineSelectionArea(anchor = previous?.anchor ?: point, focus = point))
    }

    fun moveVertical(direction: Int, extend: Boolean): Boolean {
        val previous = current() ?: return false
        val target = (previous.focus.trackIndex + direction).coerceIn(minimumValue = 0, maximumValue = TimelineRepository.tracks.value.lastIndex)
        if (target == previous.focus.trackIndex) {
            return true
        }
        val point = previous.focus.copy(trackIndex = target)
        publish(value = TimelineSelectionArea(anchor = if (extend) previous.anchor else point, focus = point))
        return true
    }

    fun moveHorizontal(intervals: GridIntervals, direction: Int, extend: Boolean): Long? {
        val previous = current() ?: return null
        val selection = if (previous.anchor.timeMs == previous.focus.timeMs) {
            Selectable.TimelineTime(trackIndex = previous.focus.trackIndex, timeMs = previous.focus.timeMs)
        } else {
            Selectable.TimelineRange(
                trackIndex = previous.focus.trackIndex,
                startMs = minOf(previous.anchor.timeMs, previous.focus.timeMs),
                endMs = maxOf(previous.anchor.timeMs, previous.focus.timeMs),
                anchorTimeMs = previous.anchor.timeMs,
            )
        }
        val next = adjacentTimelineTimeSelection(selection = selection, intervals = intervals, direction = direction, extend = extend)
            ?: return null
        val time = when (next) {
            is Selectable.TimelineTime -> next.timeMs
            is Selectable.TimelineRange -> if (next.anchorTimeMs == next.startMs) next.endMs else next.startMs
            else -> return null
        }
        val point = previous.focus.copy(timeMs = time)
        publish(value = TimelineSelectionArea(anchor = if (extend) previous.anchor else point, focus = point))
        return time
    }

    private fun publish(value: TimelineSelectionArea) {
        if (value == area && SelectionManager.selections.value == projection && current() == value) {
            return
        }
        val indices = if (value.focus.trackIndex >= value.anchor.trackIndex) {
            value.anchor.trackIndex..value.focus.trackIndex
        } else {
            value.anchor.trackIndex downTo value.focus.trackIndex
        }
        val start = minOf(value.anchor.timeMs, value.focus.timeMs)
        val end = maxOf(value.anchor.timeMs, value.focus.timeMs)
        projection = indices.map { index ->
            if (start == end) {
                Selectable.TimelineTime(trackIndex = index, timeMs = start)
            } else {
                Selectable.TimelineRange(trackIndex = index, startMs = start, endMs = end, anchorTimeMs = value.anchor.timeMs)
            }
        }
        area = value
        trackIds = TimelineRepository.tracks.value.map { it.trackId }
        SelectionManager.replaceSelections(updatedSelections = projection)
    }
}
