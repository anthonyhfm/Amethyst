package dev.anthonyhfm.amethyst.core.controls.clipboard

import dev.anthonyhfm.amethyst.core.controls.automapping.buildChainDeviceFromTimelineAudioEntry
import dev.anthonyhfm.amethyst.core.controls.automapping.buildChainDevicesFromTimelineAudioRange
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.buildSegment
import dev.anthonyhfm.amethyst.timeline.data.copyWithShiftedStartMs
import dev.anthonyhfm.amethyst.timeline.data.cropAudioEntryEnd
import dev.anthonyhfm.amethyst.timeline.data.deepCopy
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction

object ClipboardManager {
    private val _clipboardData: MutableStateFlow<ClipboardData?> = MutableStateFlow(null)
    val clipboardData = _clipboardData.asStateFlow()

    fun setClipboardData(data: ClipboardData) {
        _clipboardData.update { data }
    }

    fun copy(data: List<Selectable>) {
        if (data.isEmpty()) return

        when {
            data.any { it is Selectable.TimelineEntryItem } -> {
                val selections = data.filterIsInstance<Selectable.TimelineEntryItem>()
                val distinctTrackIndex = selections.map { it.trackIndex }.toSet()
                if (distinctTrackIndex.size == 1) {
                    val trackIndex = distinctTrackIndex.first()
                    val track = TimelineRepository.tracks.value.getOrNull(trackIndex)
                    
                    if (track is AudioTimelineTrack) {
                        val entries = selections.mapNotNull { sel -> track.entries[sel.entryStartMs] }
                        if (entries.isNotEmpty()) {
                            setClipboardData(ClipboardData.TimelineAudioEntries(entries.sortedBy { it.startTimeMs }.map { it.copy() }))
                            return
                        }
                    }
                    
                    if (track is MidiTimelineTrack) {
                        val midiTrack = track

                        val entries = selections.mapNotNull {
                            sel -> midiTrack.entries[sel.entryStartMs]
                        }

                        if (entries.isNotEmpty()) {
                            setClipboardData(ClipboardData.TimelineMidiEntries(
                                entries = entries.sortedBy { it.startTimeMs }.map { it.copy() },
                            ))
                            return
                        }
                    }
                }
            }

            data.any { it is Selectable.TimelineTrack } &&
                data.none { it is Selectable.TimelineEntryItem } -> {
                val trackIndices = data
                    .filterIsInstance<Selectable.TimelineTrack>()
                    .map(Selectable.TimelineTrack::trackIndex)
                    .distinct()
                    .sorted()
                val tracks = trackIndices.mapNotNull { trackIndex ->
                    TimelineRepository.tracks.value
                        .getOrNull(trackIndex)
                        ?.deepCopy(preserveTrackIdentity = false)
                }
                if (tracks.isNotEmpty()) {
                    setClipboardData(ClipboardData.TimelineTracks(tracks = tracks))
                    return
                }
            }

            data.any { it is Selectable.ChainDevice } -> {
                // Get the parent chain from first device to ensure consistent order
                val chainDevices = data.filterIsInstance<Selectable.ChainDevice>()
                val parentChain = chainDevices.firstOrNull()?.parent
                
                // Sort by index in the chain to maintain order
                val sortedDevices = if (parentChain != null) {
                    chainDevices.sortedBy { chainDevice ->
                        parentChain.devices.value.indexOfFirst { it.selectionUUID == chainDevice.device.selectionUUID }
                    }
                } else {
                    chainDevices
                }
                
                setClipboardData(
                    data = ClipboardData.ChainDevice(
                        states = sortedDevices.map { it.device.state.value },
                        type = if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.LightsChain) {
                            ClipboardData.ChainDevice.ChainType.Lights
                        } else {
                            ClipboardData.ChainDevice.ChainType.Sampling
                        }
                    )
                )
            }

            data.any { it is Selectable.KeyframeItem } -> {
                val keyframeItems = data.filterIsInstance<Selectable.KeyframeItem>()
                val frames = keyframeItems.map { keyframeItem ->
                    keyframeItem.parent.state.value.frames[keyframeItem.frameIndex]
                }

                setClipboardData(
                    data = ClipboardData.Keyframe(
                        frames = frames
                    )
                )
            }

            data.any { it is Selectable.GroupChainItem } -> {
                val groupItems = data.filterIsInstance<Selectable.GroupChainItem>()
                val firstParent = groupItems.firstOrNull()?.parent
                val sortedGroupItems = if (firstParent != null && groupItems.all { it.parent == firstParent }) {
                    groupItems.sortedBy(Selectable.GroupChainItem::groupIndex)
                } else {
                    groupItems
                }
                val groups = sortedGroupItems.map { groupItem ->
                    when (groupItem.parent) {
                        is GroupChainDevice -> groupItem.parent.state.value.groups[groupItem.groupIndex]
                        is MultiGroupChainDevice -> groupItem.parent.state.value.groups[groupItem.groupIndex]
                        else -> throw IllegalStateException("Unsupported parent type for GroupChainItem")
                    }
                }

                setClipboardData(
                    data = ClipboardData.GroupChainItem(
                        groups = groups
                    )
                )
            }

            data.any { it is Selectable.GradientStep } -> {
                val step = data.filterIsInstance<Selectable.GradientStep>().first()

                setClipboardData(
                    data = ClipboardData.GradientStep(step)
                )
            }

            data.any { it is Selectable.PianoRollNote } -> {
                val notes = data.filterIsInstance<Selectable.PianoRollNote>().map { it.note }
                setClipboardData(
                    data = ClipboardData.PianoRollNotes(
                        notes = notes.map { it.copy() }
                    )
                )
            }

            data.any { it is Selectable.VirtualViewportDevice } -> {
                println("Copying Virtual Viewport Devices is currently not supported")
            }
        }
    }

    fun paste() {
        val mode = WorkspaceRepository.mode.value

        when (val clip = clipboardData.value) {
            is ClipboardData.TimelineAudioRange -> {
                if (mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                    pasteSamplingDevices(
                        buildChainDevicesFromTimelineAudioRange(
                            entries = clip.entries,
                            automationLanes = clip.automationLanes,
                            rangeStartMs = clip.rangeStartMs
                        )
                    )
                    return
                }

                val (anchorTrackIndex, anchorTimeMs) = resolveTimelinePasteAnchor()
                val originalTrack = TimelineRepository.tracks.value.getOrNull(anchorTrackIndex) as? AudioTimelineTrack ?: return
                val beforeTrack = originalTrack.deepCopy() as AudioTimelineTrack
                val afterTrack = originalTrack.deepCopy() as AudioTimelineTrack
                val earliest = clip.entries.minOfOrNull(AudioEntry::startTimeMs) ?: clip.rangeStartMs
                var entriesChanged = false

                clip.entries.sortedBy(AudioEntry::startTimeMs).forEach { original ->
                    val offset = original.startTimeMs - earliest
                    val newStart = anchorTimeMs + offset
                    val newEntry = original.copyWithShiftedStartMs(newStart)
                    val resolved = resolveOverlapForPaste(afterTrack.entries, newEntry, original.startTimeMs)
                    if (resolved != null) {
                        afterTrack.entries[resolved.startTimeMs] = resolved
                        entriesChanged = true
                    }
                }

                val automationChanged = afterTrack.pasteAutomationLanes(
                    startMs = anchorTimeMs,
                    lanes = clip.automationLanes
                )

                if (!entriesChanged && !automationChanged) return

                TimelineRepository.replaceTrack(anchorTrackIndex, afterTrack)
                UndoManager.addAction(
                    UndoableAction.TrackStateChange(
                        trackIndex = anchorTrackIndex,
                        beforeTrack = beforeTrack,
                        afterTrack = afterTrack,
                        mergeable = false
                    )
                )
            }

            is ClipboardData.TimelineAudioEntries -> {
                if (mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                    pasteSamplingDevices(
                        clip.entries.mapNotNull(::buildChainDeviceFromTimelineAudioEntry)
                    )
                    return
                }

                val (anchorTrackIndex, anchorTimeMs) = resolveTimelinePasteAnchor()
                val track = TimelineRepository.tracks.value.getOrNull(anchorTrackIndex) as? AudioTimelineTrack ?: return
                val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                val earliest = clip.entries.minBy { it.startTimeMs }.startTimeMs

                clip.entries.sortedBy { it.startTimeMs }.forEach { original ->
                    val offset = original.startTimeMs - earliest
                    val newStart = anchorTimeMs + offset
                    val newEntry = original.copyWithShiftedStartMs(newStart)
                    val resolved = resolveOverlapForPaste(track.entries, newEntry, original.startTimeMs)
                    if (resolved != null) {
                        track.entries[resolved.startTimeMs] = resolved
                    }
                }

                val after = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }

                val current = TimelineRepository.tracks.value.toMutableList()
                val newTrack = AudioTimelineTrack().apply { entries.putAll(track.entries) }
                current[anchorTrackIndex] = newTrack
                TimelineRepository.tracks.value = current.toList()
                UndoManager.addAction(UndoableAction.TimelineChange(anchorTrackIndex, beforeEntries = before, afterEntries = after))
            }

            is ClipboardData.TimelineMidiEntries -> {
                val (anchorTrackIndex, anchorTimeMs) = resolveTimelinePasteAnchor()
                
                val track = TimelineRepository.tracks.value.getOrNull(anchorTrackIndex)
                if (track !is MidiTimelineTrack) return
                
                val midiTrack = track as TimelineTrack<MidiEntry>
                val earliest = clip.entries.minBy { it.startTimeMs }.startTimeMs

                clip.entries.sortedBy { it.startTimeMs }.forEach { original ->
                    val offset = original.startTimeMs - earliest
                    val newStart = anchorTimeMs + offset
                    val newEntry = original.copy(startTimeMs = newStart)
                    midiTrack.entries[newStart] = newEntry
                }
                
                val current = TimelineRepository.tracks.value.toMutableList()
                val newTrack = MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
                current[anchorTrackIndex] = newTrack
                TimelineRepository.tracks.value = current.toList()
            }

            is ClipboardData.ChainDevice -> {
                val clipData = clipboardData.value as ClipboardData.ChainDevice
                val selectedChainDevices = SelectionManager.selections.value.filterIsInstance<Selectable.ChainDevice>()
                val parentChain = selectedChainDevices.firstOrNull()?.parent

                if (parentChain != null) {
                    val indices = selectedChainDevices.filter { it.parent == parentChain }.map { sel ->
                        sel.parent.devices.value.indexOfFirst { it.selectionUUID == sel.device.selectionUUID }
                    }.filter { it >= 0 }
                    val baseIndex = (indices.maxOrNull()?.plus(1)) ?: parentChain.devices.value.size

                    val modeIsLights = WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.LightsChain
                    val modeIsSampling = WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain
                    if ((modeIsLights && clipData.type != ClipboardData.ChainDevice.ChainType.Lights) ||
                        (modeIsSampling && clipData.type != ClipboardData.ChainDevice.ChainType.Sampling)) {
                        return
                    }

                    clipData.states.forEachIndexed { offset, state ->
                        parentChain.add(
                            device = StateChain.unpackDevice(state),
                            atIndex = baseIndex + offset
                        )
                    }
                } else {
                    if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.LightsChain) {
                        if (clipData.type != ClipboardData.ChainDevice.ChainType.Lights) return
                        val baseIndex = WorkspaceRepository.lightsChain.devices.value.size
                        clipData.states.forEachIndexed { offset, state ->
                            WorkspaceRepository.lightsChain.add(
                                device = StateChain.unpackDevice(state),
                                atIndex = baseIndex + offset
                            )
                        }
                    } else if (WorkspaceRepository.mode.value is WorkspaceContract.WorkspaceMode.SamplingChain) {
                        if (clipData.type != ClipboardData.ChainDevice.ChainType.Sampling) return
                        val baseIndex = WorkspaceRepository.samplingChain.devices.value.size
                        clipData.states.forEachIndexed { offset, state ->
                            WorkspaceRepository.samplingChain.add(
                                device = StateChain.unpackDevice(state),
                                atIndex = baseIndex + offset
                            )
                        }
                    }
                }
            }

            is ClipboardData.Keyframe -> {
                if (mode is dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode) {
                    val keyframeData = clipboardData.value as ClipboardData.Keyframe
                    val targetIndex = SelectionManager.selections.value
                        .filterIsInstance<Selectable.KeyframeItem>()
                        .maxOfOrNull { it.frameIndex + 1 }

                    mode.parentDevice?.pasteFrames(keyframeData.frames, targetIndex)
                }
            }

            is ClipboardData.GroupChainItem -> {
                val groupData = clipboardData.value as ClipboardData.GroupChainItem
                val selectedGroups = SelectionManager.selections.value.filterIsInstance<Selectable.GroupChainItem>()

                if (selectedGroups.isNotEmpty()) {
                    val firstSelected = selectedGroups.first()
                    val targetIndex = selectedGroups.maxOfOrNull { it.groupIndex + 1 }

                    when (firstSelected.parent) {
                        is GroupChainDevice -> {
                            firstSelected.parent.pasteGroups(groupData.groups, targetIndex)
                        }
                        is MultiGroupChainDevice -> {
                            firstSelected.parent.pasteGroups(groupData.groups, targetIndex)
                        }
                    }
                }
            }

            is ClipboardData.PianoRollNotes -> {
                val pianoRollMode = WorkspaceRepository.mode.value as? dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode ?: return
                pianoRollMode.pasteNotes(clip.notes)
            }

            is ClipboardData.TimelineTracks -> {
                if (clip.tracks.isEmpty()) return

                val anchorIndex = resolveTimelineTrackPasteAnchor()
                val current = TimelineRepository.tracks.value.toMutableList()
                val insertAt = (anchorIndex + 1).coerceIn(0, current.size)
                val pastedIndices = mutableListOf<Int>()

                clip.tracks.forEachIndexed { offset, track ->
                    val targetIndex = insertAt + offset
                    current.add(targetIndex, track)
                    pastedIndices += targetIndex
                    UndoManager.addAction(
                        UndoableAction.TrackAddition(
                            trackIndex = targetIndex,
                            track = track,
                        )
                    )
                }

                TimelineRepository.updateTracksSnapshot(current.toList())
                SelectionManager.selectTimelineTracks(
                    trackIndices = pastedIndices,
                    anchorTrackIndex = pastedIndices.lastOrNull(),
                )
            }

            else -> Unit
        }
    }

    private fun resolveTimelineTrackPasteAnchor(): Int {
        val selections = SelectionManager.selections.value
        val selectedTrackIndices = selections
            .filterIsInstance<Selectable.TimelineTrack>()
            .map(Selectable.TimelineTrack::trackIndex)
        if (selectedTrackIndices.isNotEmpty()) {
            return selectedTrackIndices.max()
        }

        selections
            .filterIsInstance<Selectable.TimelineEntryItem>()
            .lastOrNull()
            ?.trackIndex
            ?.let { return it }

        SelectionManager.lastSelectedTimelineTrackIndex?.let { return it }

        return TimelineRepository.tracks.value.lastIndex.coerceAtLeast(0)
    }

    private fun resolveTimelinePasteAnchor(): Pair<Int, Long> {
        val selections = SelectionManager.selections.value
        val timeSel = selections.filterIsInstance<Selectable.TimelineTime>().firstOrNull()
        val rangeSel = selections.filterIsInstance<Selectable.TimelineRange>().firstOrNull()
        val entrySel = selections.filterIsInstance<Selectable.TimelineEntryItem>().firstOrNull()

        return when {
            timeSel != null -> timeSel.trackIndex to timeSel.timeMs
            rangeSel != null -> rangeSel.trackIndex to rangeSel.startMs
            entrySel != null -> entrySel.trackIndex to entrySel.entryStartMs
            else -> 0 to 0L
        }
    }

    private fun pasteSamplingDevices(devices: List<GenericChainDevice<*>>) {
        if (devices.isEmpty()) return

        val selectedChainDevices = SelectionManager.selections.value.filterIsInstance<Selectable.ChainDevice>()
        val parentChain = selectedChainDevices.firstOrNull()?.parent

        if (parentChain != null) {
            val indices = selectedChainDevices
                .filter { it.parent == parentChain }
                .map { selection ->
                    parentChain.devices.value.indexOfFirst { it.selectionUUID == selection.device.selectionUUID }
                }
                .filter { it >= 0 }
            val baseIndex = (indices.maxOrNull()?.plus(1)) ?: parentChain.devices.value.size

            devices.forEachIndexed { offset, device ->
                parentChain.add(
                    device = device,
                    atIndex = baseIndex + offset
                )
            }
            return
        }

        val baseIndex = WorkspaceRepository.samplingChain.devices.value.size
        devices.forEachIndexed { offset, device ->
            WorkspaceRepository.samplingChain.add(
                device = device,
                atIndex = baseIndex + offset
            )
        }
    }

    // Vereinfachte Overlap-Logik für Paste (dupliziert von TimelineViewModel, leicht angepasst)
    private fun resolveOverlapForPaste(
        entries: MutableMap<Long, AudioEntry>,
        newEntry: AudioEntry,
        originStartMs: Long
    ): AudioEntry? {
        val direction = newEntry.startTimeMs - originStartMs
        var adjustedNew = newEntry
        val toRemove = mutableListOf<Long>()
        val toReplace = mutableMapOf<Long, AudioEntry>()
        val toAdd = mutableListOf<AudioEntry>()
        val sorted = entries.values.sortedBy { it.startTimeMs }

        fun splitEntry(original: AudioEntry, splitStartMs: Long, splitEndMs: Long): Pair<AudioEntry?, AudioEntry?> {
            val left = if (original.startTimeMs < splitStartMs && original.endTimeMs > splitStartMs) {
                original.buildSegment(original.startTimeMs, splitStartMs)
            } else null
            val right = if (original.endTimeMs > splitEndMs && original.endTimeMs > splitEndMs) {
                original.buildSegment(splitEndMs, original.endTimeMs)
            } else null
            return left to right
        }

        if (direction >= 0) {
            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.startTimeMs
                if (!overlaps) return@forEach
                val existingFullyInsideNew = existing.startTimeMs >= adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val newFullyInsideExisting = existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs
                val existingOverlapsAtStart = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs && existing.endTimeMs <= adjustedNew.endTimeMs
                val existingOverlapsAtEnd = existing.startTimeMs >= adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                val existingSpansAcross = existing.startTimeMs < adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.endTimeMs
                when {
                    newFullyInsideExisting -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[existing.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                    existingFullyInsideNew -> toRemove.add(existing.startTimeMs)
                    existingOverlapsAtStart -> existing.cropAudioEntryEnd(adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)
                    existingOverlapsAtEnd -> {
                        toRemove.add(existing.startTimeMs)
                        existing.buildSegment(adjustedNew.endTimeMs, existing.endTimeMs)?.let { toAdd.add(it) }
                    }
                    existingSpansAcross -> {
                        val (left, right) = splitEntry(existing, adjustedNew.startTimeMs, adjustedNew.endTimeMs)
                        toRemove.add(existing.startTimeMs)
                        left?.let { toReplace[existing.startTimeMs] = it }
                        right?.let { toAdd.add(it) }
                    }
                }
            }
        } else {
            var earliestBlockingStart: Long? = null
            sorted.forEach { existing ->
                val overlaps = existing.startTimeMs < adjustedNew.endTimeMs && existing.endTimeMs > adjustedNew.startTimeMs
                if (!overlaps) return@forEach
                if (existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs > adjustedNew.startTimeMs) return null
                if (existing.startTimeMs <= adjustedNew.startTimeMs && existing.endTimeMs >= adjustedNew.endTimeMs) return null
                if (existing.startTimeMs > adjustedNew.startTimeMs && existing.startTimeMs < adjustedNew.endTimeMs) {
                    if (earliestBlockingStart == null || existing.startTimeMs < earliestBlockingStart!!) earliestBlockingStart = existing.startTimeMs
                }
            }
            if (earliestBlockingStart != null) {
                val newEndMs = earliestBlockingStart!!
                if (newEndMs <= adjustedNew.startTimeMs) return null
                adjustedNew = adjustedNew.cropAudioEntryEnd(newEndMs) ?: return null
            }
        }
        toRemove.forEach(entries::remove)
        toReplace.forEach { (k, v) -> entries[k] = v }
        toAdd.forEach { add -> entries[add.startTimeMs] = add }
        return adjustedNew
    }
}
