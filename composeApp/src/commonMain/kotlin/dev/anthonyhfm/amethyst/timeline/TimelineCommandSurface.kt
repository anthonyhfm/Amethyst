package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.toClipKey
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.timeline.data.MidiNote

object TimelineCommandSurface {
    private data class PendingTrackStateChange(
        val trackIndex: Int,
        val beforeTrack: TimelineTrack<*>,
        val afterTrack: TimelineTrack<*>
    )

    fun selectedTrackIndices(
        selections: List<Selectable> = SelectionManager.selections.value
    ): List<Int> {
        return SelectionManager.selectedTimelineTrackIndices(selections).sorted()
    }

    fun trackTargetsForContext(
        trackIndex: Int,
        selections: List<Selectable> = SelectionManager.selections.value
    ): List<Int> {
        val selectedTrackIndices = selectedTrackIndices(selections)

        return if (selectedTrackIndices.contains(trackIndex)) {
            selectedTrackIndices
        } else {
            listOf(trackIndex)
        }
    }

    fun trackSelectionRange(
        anchorTrackIndex: Int?,
        targetTrackIndex: Int,
        visibleTrackIndices: List<Int>
    ): List<Int> {
        val targetRowIndex = visibleTrackIndices.indexOf(targetTrackIndex)
        if (targetRowIndex < 0) {
            return listOf(targetTrackIndex)
        }

        val anchorRowIndex = anchorTrackIndex
            ?.let(visibleTrackIndices::indexOf)
            ?.takeIf { it >= 0 }
            ?: return listOf(targetTrackIndex)

        return if (anchorRowIndex <= targetRowIndex) {
            visibleTrackIndices.subList(anchorRowIndex, targetRowIndex + 1)
        } else {
            visibleTrackIndices.subList(targetRowIndex, anchorRowIndex + 1)
        }
    }

    fun selectTrackRange(
        anchorTrackIndex: Int?,
        targetTrackIndex: Int,
        visibleTrackIndices: List<Int>
    ): List<Int> {
        val trackIndices = trackSelectionRange(
            anchorTrackIndex = anchorTrackIndex,
            targetTrackIndex = targetTrackIndex,
            visibleTrackIndices = visibleTrackIndices
        )
        SelectionManager.selectTimelineTracks(
            trackIndices = trackIndices,
            anchorTrackIndex = targetTrackIndex
        )
        return trackIndices
    }

    fun groupChildTrackIndices(
        groupTrackIndex: Int,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): List<Int> = emptyList()

    fun selectGroupChildren(
        groupTrackIndex: Int,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): List<Int> = emptyList()

    fun routedSourceTrackIndices(
        trackIndex: Int,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): List<Int> = emptyList()

    fun selectRoutedSources(
        trackIndex: Int,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): List<Int> {
        val sourceTrackIndices = routedSourceTrackIndices(trackIndex, tracks)
        if (sourceTrackIndices.isNotEmpty()) {
            SelectionManager.selectTimelineTracks(
                trackIndices = sourceTrackIndices,
                anchorTrackIndex = sourceTrackIndices.lastOrNull()
            )
        }
        return sourceTrackIndices
    }

    fun entryTargetsForContext(
        trackIndex: Int,
        entryStartMs: Long,
        selections: List<Selectable> = SelectionManager.selections.value
    ): List<TimelineClipKey> {
        val selectedEntries = selections
            .filterIsInstance<Selectable.TimelineEntryItem>()
            .map(Selectable.TimelineEntryItem::toClipKey)
            .distinct()
            .sortedWith(compareBy(TimelineClipKey::trackIndex, TimelineClipKey::entryStartMs))
        val clickedEntry = TimelineClipKey(trackIndex = trackIndex, entryStartMs = entryStartMs)

        return if (selectedEntries.contains(clickedEntry)) {
            selectedEntries
        } else {
            listOf(clickedEntry)
        }
    }

    fun duplicateEntries(targets: List<TimelineClipKey>): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            targets
                .groupBy(TimelineClipKey::trackIndex)
                .map { (trackIndex, entries) ->
                    TimelineEditCommand.DuplicateEntries(
                        trackIndex = trackIndex,
                        entryStartTimes = entries.map(TimelineClipKey::entryStartMs)
                    )
                }
        )

        selectCreatedEntries(result.createdEntries)
        return result
    }

    fun deleteEntries(targets: List<TimelineClipKey>): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            targets
                .groupBy(TimelineClipKey::trackIndex)
                .map { (trackIndex, entries) ->
                    TimelineEditCommand.DeleteEntries(
                        trackIndex = trackIndex,
                        entryStartTimes = entries.map(TimelineClipKey::entryStartMs)
                    )
                }
        )

        if (result.didChange) {
            SelectionManager.clear()
        }

        return result
    }

    fun createNotes(trackIndex: Int, entryStartMs: Long, notes: List<MidiNote>): TimelineCommandResult {
        return TimelineCommandExecutor.execute(
            TimelineEditCommand.CreateNotes(
                trackIndex = trackIndex,
                entryStartTime = entryStartMs,
                notes = notes
            )
        )
    }

    fun moveNotes(trackIndex: Int, entryStartMs: Long, changes: List<TimelineEditedNote>): TimelineCommandResult {
        return TimelineCommandExecutor.execute(
            TimelineEditCommand.MoveNotes(
                trackIndex = trackIndex,
                entryStartTime = entryStartMs,
                changes = changes
            )
        )
    }

    fun resizeNotes(trackIndex: Int, entryStartMs: Long, changes: List<TimelineEditedNote>): TimelineCommandResult {
        return TimelineCommandExecutor.execute(
            TimelineEditCommand.ResizeNotes(
                trackIndex = trackIndex,
                entryStartTime = entryStartMs,
                changes = changes
            )
        )
    }

    fun updateNotes(trackIndex: Int, entryStartMs: Long, changes: List<TimelineEditedNote>): TimelineCommandResult {
        return TimelineCommandExecutor.execute(
            TimelineEditCommand.UpdateNotes(
                trackIndex = trackIndex,
                entryStartTime = entryStartMs,
                changes = changes
            )
        )
    }

    fun deleteNotes(trackIndex: Int, entryStartMs: Long, notes: List<MidiNote>): TimelineCommandResult {
        return TimelineCommandExecutor.execute(
            TimelineEditCommand.DeleteNotes(
                trackIndex = trackIndex,
                entryStartTime = entryStartMs,
                notes = notes
            )
        )
    }

    fun createAutomationPoints(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        points: List<TimelineAutomationPoint>
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.CreateAutomationPoints(
                trackIndex = trackIndex,
                lane = lane,
                points = points
            )
        )

        if (result.didChange) {
            SelectionManager.selectTimelineAutomationPoints(
                trackIndex = trackIndex,
                lane = lane,
                pointIds = points.map(TimelineAutomationPoint::pointId)
            )
        }

        return result
    }

    fun moveAutomationPoints(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        changes: List<TimelineEditedAutomationPoint>
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.MoveAutomationPoints(
                trackIndex = trackIndex,
                lane = lane,
                changes = changes
            )
        )

        if (result.didChange) {
            SelectionManager.selectTimelineAutomationPoints(
                trackIndex = trackIndex,
                lane = lane,
                pointIds = changes.map(TimelineEditedAutomationPoint::after).map(TimelineAutomationPoint::pointId)
            )
        }

        return result
    }

    fun deleteAutomationPoints(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        pointIds: List<String>
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.DeleteAutomationPoints(
                trackIndex = trackIndex,
                lane = lane,
                pointIds = pointIds
            )
        )

        if (result.didChange) {
            SelectionManager.selectTimelineAutomationLane(
                trackIndex = trackIndex,
                target = lane.target,
                bindingId = lane.bindingId
            )
        }

        return result
    }

    fun deleteAutomationRange(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.DeleteAutomationRange(
                trackIndex = trackIndex,
                lane = lane,
                startMs = startMs,
                endMs = endMs
            )
        )

        if (result.didChange) {
            SelectionManager.selectTimelineAutomationLane(
                trackIndex = trackIndex,
                target = lane.target,
                bindingId = lane.bindingId
            )
        }

        return result
    }

    fun duplicateAutomationRange(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        startMs: Long,
        endMs: Long
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.DuplicateAutomationRange(
                trackIndex = trackIndex,
                lane = lane,
                startMs = startMs,
                endMs = endMs
            )
        )

        if (result.didChange) {
            SelectionManager.replaceSelections(
                listOf(
                    Selectable.TimelineAutomationLane(
                        trackIndex = trackIndex,
                        target = lane.target,
                        bindingId = lane.bindingId
                    ),
                    Selectable.TimelineRange(
                        trackIndex = trackIndex,
                        startMs = endMs,
                        endMs = endMs + (endMs - startMs)
                    )
                )
            )
        }

        return result
    }

    fun setAutomationLaneVisibility(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        visible: Boolean
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.SetAutomationLaneVisibility(
                trackIndex = trackIndex,
                lane = lane,
                visible = visible
            )
        )

        if (result.didChange) {
            if (visible) {
                SelectionManager.selectTimelineAutomationLane(
                    trackIndex = trackIndex,
                    target = lane.target,
                    bindingId = lane.bindingId
                )
            } else {
                removeAutomationSelections { selection ->
                    selection.trackIndex == trackIndex && selection.laneKey == lane.normalized()
                }
            }
        }

        return result
    }

    fun setAutomationLaneEnabled(
        trackIndex: Int,
        lane: TimelineAutomationLaneKey,
        enabled: Boolean
    ): TimelineCommandResult {
        val result = TimelineCommandExecutor.execute(
            TimelineEditCommand.SetAutomationLaneEnabled(
                trackIndex = trackIndex,
                lane = lane,
                enabled = enabled
            )
        )

        if (result.didChange) {
            SelectionManager.selectTimelineAutomationLane(
                trackIndex = trackIndex,
                target = lane.target,
                bindingId = lane.bindingId
            )
        }

        return result
    }

    fun requestTrackRename(trackIndex: Int) {
        SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
        SelectionManager.renameRequest.tryEmit(SelectionManager.RenameTarget.Track(trackIndex = trackIndex))
    }

    fun requestEntryRename(trackIndex: Int, entryStartMs: Long) {
        SelectionManager.select(
            Selectable.TimelineEntryItem(
                trackIndex = trackIndex,
                entryStartMs = entryStartMs
            )
        )
        SelectionManager.renameRequest.tryEmit(SelectionManager.RenameTarget.TimelineEntry(
            trackIndex = trackIndex,
            entryStartMs = entryStartMs
        ))
    }

    fun requestRenameForSelection(
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        val trackTargets = selectedTrackIndices(selections)
        if (trackTargets.size == 1) {
            requestTrackRename(trackTargets.single())
            return true
        }

        val entryTargets = selections
            .filterIsInstance<Selectable.TimelineEntryItem>()
            .distinctBy { it.trackIndex to it.entryStartMs }
        if (entryTargets.size == 1) {
            val entryTarget = entryTargets.single()
            requestEntryRename(
                trackIndex = entryTarget.trackIndex,
                entryStartMs = entryTarget.entryStartMs
            )
            return true
        }

        return false
    }

    fun setTrackAutomationVisibility(
        trackIndex: Int,
        visible: Boolean,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value,
    ): TimelineCommandResult {
        val track = tracks.getOrNull(trackIndex) ?: return TimelineCommandResult()
        val laneKeys = trackAutomationLaneKeys(track)
        if (laneKeys.isEmpty()) return TimelineCommandResult()

        val result = updateTrackStates(trackIndices = listOf(trackIndex)) {
            laneKeys.fold(initial = false) { didChange, laneKey ->
                setAutomationLaneVisibility(
                    key = laneKey,
                    visible = visible
                ) || didChange
            }
        }

        if (visible) {
            val laneKey = laneKeys.first()
            val laneSelection = Selectable.TimelineAutomationLane(
                trackIndex = trackIndex,
                target = laneKey.target,
                bindingId = laneKey.bindingId,
            )
            val preservedSelections = SelectionManager.selections.value.filterNot {
                it is Selectable.TimelineAutomationLane || it is Selectable.TimelineAutomationPoint
            }
            val updatedSelections = if (preservedSelections.isEmpty()) {
                listOf(Selectable.TimelineTrack(trackIndex = trackIndex), laneSelection)
            } else {
                preservedSelections + laneSelection
            }
            SelectionManager.replaceSelections(updatedSelections)
        } else {
            removeAutomationSelections { selection ->
                selection.trackIndex == trackIndex
            }
        }

        return result
    }

    fun toggleTrackAutomationVisibility(
        trackIndex: Int,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): TimelineCommandResult {
        val track = tracks.getOrNull(trackIndex) ?: return TimelineCommandResult()
        return setTrackAutomationVisibility(
            trackIndex = trackIndex,
            visible = track.automationLanes.none { lane -> lane.visible },
            tracks = tracks
        )
    }

    fun toggleAutomationForSelection(
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value,
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        val trackIndex = primaryTrackIndexForSelection(
            tracks = tracks,
            selections = selections
        ) ?: return false

        return toggleTrackAutomationVisibility(
            trackIndex = trackIndex,
            tracks = tracks
        ).didChange
    }

    fun toggleTrackMute(
        trackIndices: Collection<Int>,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): Boolean {
        val indices = trackIndices.filter { it in tracks.indices }.distinct()
        if (indices.isEmpty()) return false
        return updateTrackStates(indices) {
            isMuted = !isMuted
            true
        }.didChange
    }

    fun toggleTrackSolo(
        trackIndices: Collection<Int>,
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value
    ): Boolean {
        val indices = trackIndices.filter { it in tracks.indices }.distinct()
        if (indices.isEmpty()) return false
        return updateTrackStates(indices) {
            isSoloed = !isSoloed
            true
        }.didChange
    }

    fun toggleMuteForSelection(
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value,
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        val indices = trackIndicesForShortcutTarget(tracks = tracks, selections = selections)
        return toggleTrackMute(indices, tracks)
    }

    fun toggleSoloForSelection(
        tracks: List<TimelineTrack<*>> = TimelineRepository.tracks.value,
        selections: List<Selectable> = SelectionManager.selections.value
    ): Boolean {
        val indices = trackIndicesForShortcutTarget(tracks = tracks, selections = selections)
        return toggleTrackSolo(indices, tracks)
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

    private fun trackIndicesForShortcutTarget(
        tracks: List<TimelineTrack<*>>,
        selections: List<Selectable>
    ): List<Int> {
        val selectedTracks = selectedTrackIndices(selections).filter { it in tracks.indices }
        if (selectedTracks.isNotEmpty()) return selectedTracks

        val entryTrackIndices = selections
            .filterIsInstance<Selectable.TimelineEntryItem>()
            .map(Selectable.TimelineEntryItem::trackIndex)
            .distinct()
            .filter { it in tracks.indices }
        if (entryTrackIndices.isNotEmpty()) return entryTrackIndices

        selections
            .filterIsInstance<Selectable.TimelineAutomationLane>()
            .lastOrNull()
            ?.trackIndex
            ?.takeIf { it in tracks.indices }
            ?.let { return listOf(it) }

        val timeTrackIndices = selections
            .filterIsInstance<Selectable.TimelineTime>()
            .map(Selectable.TimelineTime::trackIndex)
            .distinct()
            .filter { it in tracks.indices }
        if (timeTrackIndices.isNotEmpty()) return timeTrackIndices

        val rangeTrackIndices = selections
            .filterIsInstance<Selectable.TimelineRange>()
            .map(Selectable.TimelineRange::trackIndex)
            .distinct()
            .filter { it in tracks.indices }
        if (rangeTrackIndices.isNotEmpty()) return rangeTrackIndices

        return SelectionManager.lastSelectedTimelineTrackIndex
            ?.takeIf { it in tracks.indices }
            ?.let { listOf(it) }
            .orEmpty()
    }

    private fun primaryTrackIndexForSelection(
        tracks: List<TimelineTrack<*>>,
        selections: List<Selectable>
    ): Int? {
        val indices = trackIndicesForShortcutTarget(tracks = tracks, selections = selections)
        if (indices.isEmpty()) return null
        if (indices.size == 1) return indices.single()

        return SelectionManager.lastSelectedTimelineTrackIndex
            ?.takeIf { it in indices }
            ?: indices.last()
    }

    private fun trackAutomationLaneKeys(track: TimelineTrack<*>): List<TimelineAutomationLaneKey> {
        val visibleKeys = track.automationLanes
            .filter { it.visible }
            .map { it.key }

        val defaultKeys = buildList {
            if (track is AudioTimelineTrack) {
                add(TimelineAutomationLaneKey(TimelineTrackAutomationTarget.VOLUME))
            }
        }

        return (visibleKeys + defaultKeys).distinct()
    }

    private fun removeAutomationSelections(
        predicate: (Selectable.TimelineAutomationLane) -> Boolean
    ) {
        val currentSelections = SelectionManager.selections.value
        val updatedSelections = currentSelections.filterNot { selection ->
            val automationLaneSelection = selection as? Selectable.TimelineAutomationLane
                ?: return@filterNot false
            predicate(automationLaneSelection)
        }

        if (updatedSelections.size != currentSelections.size) {
            SelectionManager.replaceSelections(updatedSelections)
        }
    }

    private fun updateTrackStates(
        trackIndices: Collection<Int>,
        mutate: TimelineTrack<*>.() -> Boolean
    ): TimelineCommandResult {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val pendingChanges = trackIndices
            .distinct()
            .sorted()
            .mapNotNull { trackIndex ->
                val track = currentTracks.getOrNull(trackIndex) ?: return@mapNotNull null
                val beforeTrack = track.deepCopy()
                val afterTrack = track.deepCopy()
                if (!afterTrack.mutate()) {
                    null
                } else {
                    PendingTrackStateChange(
                        trackIndex = trackIndex,
                        beforeTrack = beforeTrack,
                        afterTrack = afterTrack
                    )
                }
            }

        if (pendingChanges.isEmpty()) {
            return TimelineCommandResult()
        }

        pendingChanges.forEach { change ->
            currentTracks[change.trackIndex] = change.afterTrack
        }

        TimelineRepository.updateTracksSnapshot(currentTracks.toList())
        pendingChanges.forEach { change ->
            UndoManager.addAction(
                UndoableAction.TrackStateChange(
                    trackIndex = change.trackIndex,
                    beforeTrack = change.beforeTrack,
                    afterTrack = change.afterTrack,
                    mergeable = false
                )
            )
        }

        return TimelineCommandResult(didChange = true)
    }
}
