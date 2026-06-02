package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.controls.automapping.AutomappingManager
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.utils.TimelineClipUtils
import dev.anthonyhfm.amethyst.timeline.TimelineRepository

object TimelineKeyHandler {
    fun canCopySelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return activeAutomationRangeSelection(selections) != null ||
            selections.any { it is Selectable.TimelineRange } ||
            selections.any { it is Selectable.TimelineEntryItem }
    }

    fun canCutSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean = canCopySelection(selections)

    fun canDeleteSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return activeAutomationRangeSelection(selections) != null ||
            selections.any { it is Selectable.TimelineRange } ||
            selections.any { it is Selectable.TimelineAutomationPoint } ||
            selections.any { it is Selectable.TimelineTrack } ||
            selections.any { it is Selectable.TimelineEntryItem }
    }

    fun canDuplicateSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        return activeAutomationRangeSelection(selections) != null ||
            selections.any { it is Selectable.TimelineRange } ||
            selections.any { it is Selectable.TimelineTrack } ||
            selections.any { it is Selectable.TimelineEntryItem }
    }

    fun canRenameSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        val trackTargets = TimelineCommandSurface.selectedTrackIndices(selections)
        if (trackTargets.size == 1) return true

        return selections
            .filterIsInstance<Selectable.TimelineEntryItem>()
            .distinctBy { it.trackIndex to it.entryStartMs }
            .size == 1
    }

    fun renameSelection(): Boolean = handleRename()

    fun copySelection(): Boolean = handleCopy()

    fun cutSelection(): Boolean = handleCut()

    fun deleteSelection(): Boolean = handleDelete()

    fun duplicateSelection(): Boolean = handleDuplicate()

    fun handleKeyInput(keyEvent: KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false

        return when {
            keyEvent.key == Key.Spacebar && keyEvent.hasNoShortcutModifier() -> handleTogglePlayPause()
            keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.R -> renameSelection()
            keyEvent.key == Key.A && keyEvent.isAltPressed -> handleAutomappingTrigger()
            keyEvent.key == Key.A && keyEvent.hasNoShortcutModifier() -> handleToggleAutomation()
            keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.E -> handleCutAtSelection()
            keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.C -> copySelection()
            keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.X -> cutSelection()
            keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.V -> {
                ClipboardManager.paste()
                true
            }

            keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace -> deleteSelection()
            keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.D -> duplicateSelection()

            // Track navigation: ↑/↓ with optional Shift to extend selection
            !keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.DirectionUp ->
                handleTrackNavigation(direction = -1, extend = keyEvent.isShiftPressed)
            !keyEvent.hasPrimaryShortcutModifier() && keyEvent.key == Key.DirectionDown ->
                handleTrackNavigation(direction = +1, extend = keyEvent.isShiftPressed)

            else -> false
        }
    }

    internal fun handleTogglePlayPause(): Boolean {
        if (TimelineRepository.isPlaying.value) {
            TimelineRepository.pause()
        } else {
            val selectedEntry = SelectionManager.selections.value
                .filterIsInstance<Selectable.TimelineEntryItem>()
                .minByOrNull { it.entryStartMs }
            if (selectedEntry != null) {
                TimelineRepository.setPlayheadPosition(selectedEntry.entryStartMs)
            } else {
                val selectionTime = SelectionManager.selections.value
                    .filterIsInstance<Selectable.TimelineTime>()
                    .firstOrNull()
                if (selectionTime != null) {
                    TimelineRepository.setPlayheadPosition(selectionTime.timeMs)
                }
            }
            TimelineRepository.play()
        }
        return true
    }

    private fun handleAutomappingTrigger(): Boolean {
        // Capture ALT+A when automapping is active so timeline automation toggle is not triggered.
        // AutomappingManager.handleKeyEvent already ran before this handler in WorkspaceWindow,
        // updating isTriggerHeld — this just prevents the event from leaking into timeline handling.
        return AutomappingManager.state.value.isActive
    }

    private fun handleRename(): Boolean {
        return TimelineCommandSurface.requestRenameForSelection()
    }

    private fun handleToggleAutomation(): Boolean {
        return TimelineCommandSurface.toggleAutomationForSelection()
    }

    private fun handleCutAtSelection(): Boolean {
        val selection = SelectionManager.selections.value
            .filterIsInstance<Selectable.TimelineTime>()
            .firstOrNull()

        return selection?.let(TimelineClipUtils::cutAtSelection) ?: false
    }

    private fun handleCopy(): Boolean {
        activeAutomationRangeSelection()?.let { (automationLaneSelection, rangeSelection) ->
            return handleAutomationRangeCopy(automationLaneSelection, rangeSelection)
        }

        val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
        if (rangeSelections.isNotEmpty()) {
            return handleRangeCopy(rangeSelections)
        }

        val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
        if (entrySelections.isNotEmpty()) {
            ClipboardManager.copy(entrySelections)
            return true
        }

        return false
    }

    private fun handleCut(): Boolean {
        activeAutomationRangeSelection()?.let { (automationLaneSelection, rangeSelection) ->
            if (!handleAutomationRangeCopy(automationLaneSelection, rangeSelection)) {
                return false
            }

            return TimelineCommandSurface.deleteAutomationRange(
                trackIndex = automationLaneSelection.trackIndex,
                lane = automationLaneSelection.laneKey,
                startMs = rangeSelection.startMs,
                endMs = rangeSelection.endMs
            ).didChange
        }

        val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
        if (rangeSelections.isNotEmpty()) {
            handleRangeCopy(rangeSelections)
            TimelineCommandExecutor.execute(
                rangeSelections.map {
                    TimelineEditCommand.DeleteRange(
                        trackIndex = it.trackIndex,
                        startMs = it.startMs,
                        endMs = it.endMs
                    )
                }
            )
            SelectionManager.clear()
            return true
        }

        val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
        if (entrySelections.isNotEmpty()) {
            ClipboardManager.copy(entrySelections)
            TimelineCommandExecutor.execute(
                entrySelections.groupBy { it.trackIndex }.map { (trackIndex, selections) ->
                    TimelineEditCommand.DeleteEntries(
                        trackIndex = trackIndex,
                        entryStartTimes = selections.map { it.entryStartMs }
                    )
                }
            )
            SelectionManager.clear()
            return true
        }

        return false
    }

    private fun handleDelete(): Boolean {
        activeAutomationRangeSelection()?.let { (automationLaneSelection, rangeSelection) ->
            return TimelineCommandSurface.deleteAutomationRange(
                trackIndex = automationLaneSelection.trackIndex,
                lane = automationLaneSelection.laneKey,
                startMs = rangeSelection.startMs,
                endMs = rangeSelection.endMs
            ).didChange
        }

        val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
        if (rangeSelections.isNotEmpty()) {
            TimelineCommandExecutor.execute(
                rangeSelections.map {
                    TimelineEditCommand.DeleteRange(
                        trackIndex = it.trackIndex,
                        startMs = it.startMs,
                        endMs = it.endMs
                    )
                }
            )
            SelectionManager.clear()
            return true
        }

        val automationPointSelections = SelectionManager.selections.value
            .filterIsInstance<Selectable.TimelineAutomationPoint>()
        if (automationPointSelections.isNotEmpty()) {
            TimelineCommandExecutor.execute(
                automationPointSelections
                    .groupBy { selection -> Pair(selection.trackIndex, selection.laneKey) }
                    .map { entry ->
                        val trackIndex = entry.key.first
                        val laneKey = entry.key.second
                        TimelineEditCommand.DeleteAutomationPoints(
                            trackIndex = trackIndex,
                            lane = laneKey,
                            pointIds = entry.value.map(Selectable.TimelineAutomationPoint::pointId)
                        )
                    }
            )
            SelectionManager.clearTimelineAutomationSelections()
            return true
        }

        val trackSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTrack>()
        if (trackSelections.isNotEmpty()) {
            TimelineCommandExecutor.execute(
                TimelineEditCommand.DeleteTracks(trackSelections.map { it.trackIndex })
            )
            SelectionManager.clear()
            return true
        }

        val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
        if (entrySelections.isNotEmpty()) {
            TimelineCommandExecutor.execute(
                entrySelections.groupBy { it.trackIndex }.map { (trackIndex, selections) ->
                    TimelineEditCommand.DeleteEntries(
                        trackIndex = trackIndex,
                        entryStartTimes = selections.map { it.entryStartMs }
                    )
                }
            )
            SelectionManager.clear()
            return true
        }

        return false
    }

    private fun handleDuplicate(): Boolean {
        activeAutomationRangeSelection()?.let { (automationLaneSelection, rangeSelection) ->
            return TimelineCommandSurface.duplicateAutomationRange(
                trackIndex = automationLaneSelection.trackIndex,
                lane = automationLaneSelection.laneKey,
                startMs = rangeSelection.startMs,
                endMs = rangeSelection.endMs
            ).didChange
        }

        val rangeSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineRange>()
        if (rangeSelections.isNotEmpty()) {
            TimelineCommandExecutor.execute(
                rangeSelections.map {
                    TimelineEditCommand.DuplicateRange(
                        trackIndex = it.trackIndex,
                        startMs = it.startMs,
                        endMs = it.endMs
                    )
                }
            )
            return true
        }

        val trackSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTrack>()
        if (trackSelections.isNotEmpty()) {
            TimelineCommandExecutor.execute(
                TimelineEditCommand.DuplicateTracks(trackSelections.map { it.trackIndex })
            )
            return true
        }

        val entrySelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineEntryItem>()
        if (entrySelections.isNotEmpty()) {
            val result = TimelineCommandExecutor.execute(
                entrySelections.groupBy { it.trackIndex }.map { (trackIndex, selections) ->
                    TimelineEditCommand.DuplicateEntries(
                        trackIndex = trackIndex,
                        entryStartTimes = selections.map { it.entryStartMs }
                    )
                }
            )
            selectCreatedEntries(result.createdEntries)
            return true
        }

        return false
    }

    private fun handleRangeCopy(rangeSelections: List<Selectable.TimelineRange>): Boolean {
        rangeSelections.forEach { range ->
            when (val track = TimelineRepository.tracks.value.getOrNull(range.trackIndex)) {
                is AudioTimelineTrack -> {
                    val entries = TimelineCommandExecutor.getAudioEntriesInRange(track, range.startMs, range.endMs)
                    val automationLanes = track.automationLanesInRange(range.startMs, range.endMs)
                    if (entries.isNotEmpty() || automationLanes.isNotEmpty()) {
                        ClipboardManager.setClipboardData(
                            ClipboardData.TimelineAudioRange(
                                entries = entries,
                                automationLanes = automationLanes,
                                rangeStartMs = range.startMs,
                                rangeEndMs = range.endMs
                            )
                        )
                    }
                }

                is MidiTimelineTrack -> {
                    val entries = TimelineCommandExecutor.getMidiEntriesInRange(track, range.startMs, range.endMs)
                    if (entries.isNotEmpty()) {
                        ClipboardManager.setClipboardData(ClipboardData.TimelineMidiEntries(entries))
                    }
                }

                else -> Unit
            }
        }
        return true
    }

    private fun activeAutomationRangeSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Pair<Selectable.TimelineAutomationLane, Selectable.TimelineRange>? {
        val automationLaneSelection = selections
            .filterIsInstance<Selectable.TimelineAutomationLane>()
            .lastOrNull()
            ?: return null
        val rangeSelection = selections
            .filterIsInstance<Selectable.TimelineRange>()
            .firstOrNull { it.trackIndex == automationLaneSelection.trackIndex }
            ?: return null

        return automationLaneSelection to rangeSelection
    }

    private fun handleAutomationRangeCopy(
        automationLaneSelection: Selectable.TimelineAutomationLane,
        rangeSelection: Selectable.TimelineRange
    ): Boolean {
        val track = TimelineRepository.tracks.value
            .getOrNull(automationLaneSelection.trackIndex) as? AudioTimelineTrack
            ?: return false
        val lane = track.automationLaneInRange(
            key = automationLaneSelection.laneKey,
            startMs = rangeSelection.startMs,
            endMs = rangeSelection.endMs
        ) ?: return false

        ClipboardManager.setClipboardData(
            ClipboardData.TimelineAudioRange(
                entries = emptyList(),
                automationLanes = listOf(lane),
                rangeStartMs = rangeSelection.startMs,
                rangeEndMs = rangeSelection.endMs
            )
        )
        return true
    }

    private fun selectCreatedEntries(createdEntries: List<TimelineCreatedEntry>) {
        if (createdEntries.isEmpty()) return

        SelectionManager.clear()
        createdEntries.forEach { entry ->
            SelectionManager.select(
                Selectable.TimelineEntryItem(
                    trackIndex = entry.trackIndex,
                    entryStartMs = entry.entryStartMs
                ),
                single = false
            )
        }
    }

    private fun handleTrackNavigation(direction: Int, extend: Boolean): Boolean {
        val tracks = TimelineRepository.tracks.value
        if (tracks.isEmpty()) return false

        val trackSelections = SelectionManager.selections.value.filterIsInstance<Selectable.TimelineTrack>()

        val anchorIndex = if (trackSelections.isNotEmpty()) {
            if (direction < 0) trackSelections.minOf { it.trackIndex }
            else trackSelections.maxOf { it.trackIndex }
        } else {
            if (direction < 0) tracks.lastIndex else 0
        }

        val targetIndex = (anchorIndex + direction).coerceIn(0, tracks.lastIndex)
        if (targetIndex == anchorIndex && trackSelections.isNotEmpty()) return true

        SelectionManager.select(
            Selectable.TimelineTrack(targetIndex),
            single = !extend
        )
        return true
    }

    private fun KeyEvent.hasPrimaryShortcutModifier(): Boolean {
        return isCtrlPressed || isMetaPressed
    }

    private fun KeyEvent.hasNoShortcutModifier(): Boolean {
        return !isCtrlPressed && !isMetaPressed && !isShiftPressed && !isAltPressed
    }
}
