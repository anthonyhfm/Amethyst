package dev.anthonyhfm.amethyst.core.controls.clipboard

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
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
                } else {

                }
            }

            data.any { it is Selectable.ChainDevice } -> {
                setClipboardData(
                    data = ClipboardData.ChainDevice(
                        states = data.filterIsInstance<Selectable.ChainDevice>().map { it.device.state.value },
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
                val groups = groupItems.map { groupItem ->
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

            data.any { it is Selectable.VirtualViewportDevice } -> {
                println("Copying Virtual Viewport Devices is currently not supported")
            }
        }
    }

    fun paste() {
        val mode = WorkspaceRepository.mode.value

        when (val clip = clipboardData.value) {
            is ClipboardData.TimelineAudioEntries -> {
                val selections = SelectionManager.selections.value
                val timeSel = selections.filterIsInstance<Selectable.TimelineTime>().firstOrNull()
                val entrySel = selections.filterIsInstance<Selectable.TimelineEntryItem>().firstOrNull()
                val anchorTrackIndex: Int
                val anchorTimeMs: Long
                if (timeSel != null) {
                    anchorTrackIndex = timeSel.trackIndex
                    anchorTimeMs = timeSel.timeMs
                } else if (entrySel != null) {
                    anchorTrackIndex = entrySel.trackIndex
                    anchorTimeMs = entrySel.entryStartMs
                } else {
                    anchorTrackIndex = 0
                    anchorTimeMs = 0L
                }
                val track = TimelineRepository.tracks.value.getOrNull(anchorTrackIndex) as? AudioTimelineTrack ?: return
                val before = track.entries.values.sortedBy { it.startTimeMs }.map { it.copy() }
                val earliest = clip.entries.minBy { it.startTimeMs }.startTimeMs

                clip.entries.sortedBy { it.startTimeMs }.forEach { original ->
                    val offset = original.startTimeMs - earliest
                    val newStart = anchorTimeMs + offset
                    val newEntry = original.copy(startTimeMs = newStart)
                    val resolved = resolveOverlapForPaste(track, newEntry, original.startTimeMs)
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
                val selections = SelectionManager.selections.value
                val timeSel = selections.filterIsInstance<Selectable.TimelineTime>().firstOrNull()
                val entrySel = selections.filterIsInstance<Selectable.TimelineEntryItem>().firstOrNull()
                val anchorTrackIndex: Int
                val anchorTimeMs: Long
                if (timeSel != null) {
                    anchorTrackIndex = timeSel.trackIndex
                    anchorTimeMs = timeSel.timeMs
                } else if (entrySel != null) {
                    anchorTrackIndex = entrySel.trackIndex
                    anchorTimeMs = entrySel.entryStartMs
                } else {
                    anchorTrackIndex = 0
                    anchorTimeMs = 0L
                }
                
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

            else -> {
                println("You cannot copy this right now")
            }
        }
    }

    // Vereinfachte Overlap-Logik für Paste (dupliziert von TimelineViewModel, leicht angepasst)
    private fun resolveOverlapForPaste(track: AudioTimelineTrack, newEntry: AudioEntry, originStartMs: Long): AudioEntry? {
        val direction = newEntry.startTimeMs - originStartMs
        var adjustedNew = newEntry
        val toRemove = mutableListOf<Long>()
        val toReplace = mutableMapOf<Long, AudioEntry>()
        val toAdd = mutableListOf<AudioEntry>()
        val sorted = track.entries.values.sortedBy { it.startTimeMs }

        fun cropEntryEnd(entry: AudioEntry, newEndMs: Long): AudioEntry? {
            if (newEndMs <= entry.startTimeMs) return null
            val newDuration = newEndMs - entry.startTimeMs
            val raw = entry.rawData
            val croppedData = if (raw != null && raw.isNotEmpty()) {
                val bytesPerSample = (entry.bitDepth / 8) * entry.channels
                val samplesPerMs = entry.sampleRate.toDouble() / 1000.0
                val totalSamplesExact = newDuration * samplesPerMs
                val totalSamples = kotlin.math.round(totalSamplesExact).toLong()
                val totalBytes = (totalSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
                raw.sliceArray(0 until totalBytes)
            } else raw
            return entry.copy(durationMs = newDuration, rawData = croppedData)
        }
        fun buildEntrySegment(original: AudioEntry, segStartMs: Long, segEndMs: Long): AudioEntry? {
            if (segEndMs <= segStartMs) return null
            val raw = original.rawData ?: return original.copy(startTimeMs = segStartMs, durationMs = segEndMs - segStartMs)
            val bytesPerSample = (original.bitDepth / 8) * original.channels
            val samplesPerMs = original.sampleRate.toDouble() / 1000.0
            val startSamplesExact = (segStartMs - original.startTimeMs) * samplesPerMs
            val endSamplesExact = (segEndMs - original.startTimeMs) * samplesPerMs
            val startSamples = kotlin.math.round(startSamplesExact).toLong().coerceAtLeast(0)
            val endSamples = kotlin.math.round(endSamplesExact).toLong().coerceAtLeast(startSamples)
            val startByte = (startSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            val endByte = (endSamples * bytesPerSample).toInt().coerceAtMost(raw.size)
            if (startByte >= endByte) return null
            val slice = raw.sliceArray(startByte until endByte)
            return original.copy(startTimeMs = segStartMs, durationMs = segEndMs - segStartMs, rawData = slice)
        }
        fun splitEntry(original: AudioEntry, splitStartMs: Long, splitEndMs: Long): Pair<AudioEntry?, AudioEntry?> {
            val left = if (original.startTimeMs < splitStartMs && original.endTimeMs > original.startTimeMs) {
                buildEntrySegment(original, original.startTimeMs, splitStartMs)
            } else null
            val right = if (original.endTimeMs > splitEndMs && original.endTimeMs > splitEndMs) {
                buildEntrySegment(original, splitEndMs, original.endTimeMs)
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
                    existingOverlapsAtStart -> cropEntryEnd(existing, adjustedNew.startTimeMs)?.let { toReplace[existing.startTimeMs] = it } ?: toRemove.add(existing.startTimeMs)
                    existingOverlapsAtEnd -> {
                        toRemove.add(existing.startTimeMs)
                        buildEntrySegment(existing, adjustedNew.endTimeMs, existing.endTimeMs)?.let { toAdd.add(it) }
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
                val newDur = newEndMs - adjustedNew.startTimeMs
                adjustedNew = adjustedNew.copy(durationMs = newDur, rawData = cropEntryEnd(adjustedNew, newEndMs)?.rawData)
            }
        }
        toRemove.forEach { track.entries.remove(it) }
        toReplace.forEach { (k, v) -> track.entries[k] = v }
        toAdd.forEach { add -> track.entries[add.startTimeMs] = add }
        return adjustedNew
    }
}