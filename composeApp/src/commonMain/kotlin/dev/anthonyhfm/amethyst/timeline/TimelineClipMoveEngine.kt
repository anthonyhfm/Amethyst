package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.buildSegment
import dev.anthonyhfm.amethyst.timeline.data.copyWithShiftedStartMs
import dev.anthonyhfm.amethyst.timeline.data.deepCopy

/** Applies a timeline drag as one deterministic, undoable overwrite edit. */
object TimelineClipMoveEngine {
    data class Placement(
        val source: TimelineClipKey,
        val targetTrackIndex: Int,
        val targetStartMs: Long,
        val durationMs: Long,
    ) {
        val targetEndMs: Long get() = targetStartMs + durationMs
    }

    data class Preview(
        val isValid: Boolean,
        val placements: List<Placement> = emptyList(),
    )

    fun preview(command: TimelineEditCommand.MoveEntries): Preview =
        preview(command, TimelineRepository.tracks.value)

    internal fun preview(
        command: TimelineEditCommand.MoveEntries,
        tracks: List<TimelineTrack<*>>,
    ): Preview {
        val anchorTrack = tracks.getOrNull(command.anchorKey.trackIndex) ?: return Preview(false)
        val anchorEntry = anchorTrack.regularEntry(command.anchorKey.entryStartMs) ?: return Preview(false)
        val trackDelta = command.targetTrackIndex - command.anchorKey.trackIndex
        val timeDelta = command.targetStartMs.coerceAtLeast(0L) - anchorEntry.startTimeMs
        val uniqueKeys = command.entryKeys.distinct().ifEmpty { listOf(command.anchorKey) }

        val placements = uniqueKeys.map { key ->
            val sourceTrack = tracks.getOrNull(key.trackIndex) ?: return Preview(false)
            if (sourceTrack::class != anchorTrack::class) return@map null
            val entry = sourceTrack.regularEntry(key.entryStartMs) ?: return Preview(false)
            val targetTrackIndex = key.trackIndex + trackDelta
            val targetTrack = tracks.getOrNull(targetTrackIndex) ?: return Preview(false)
            if (targetTrack::class != sourceTrack::class) return Preview(false)
            val targetStart = entry.startTimeMs + timeDelta
            if (targetStart < 0L) return Preview(false)
            Placement(key, targetTrackIndex, targetStart, entry.durationMs)
        }.filterNotNull()

        if (placements.none { it.source == command.anchorKey }) return Preview(false)
        if (placements.isEmpty()) return Preview(false)

        // Experimental chain-effect clips share a MIDI lane but are deliberately not
        // part of this drag system, so a regular MIDI move may not overwrite them.
        val blockedByChainEffect = placements.any { placement ->
            val target = tracks[placement.targetTrackIndex] as? MidiTimelineTrack ?: return@any false
            target.chainEffectEntries.values.any { effect ->
                effect.startTimeMs < placement.targetEndMs && effect.endTimeMs > placement.targetStartMs
            }
        }
        return Preview(isValid = !blockedByChainEffect, placements = placements)
    }

    fun execute(command: TimelineEditCommand.MoveEntries): TimelineCommandResult {
        val beforeTracks = TimelineRepository.tracks.value
        val preview = preview(command, beforeTracks)
        if (!preview.isValid) return TimelineCommandResult()

        val touchedIndices = (preview.placements.map { it.source.trackIndex } +
            preview.placements.map { it.targetTrackIndex }).distinct().sorted()
        val beforeSnapshots = touchedIndices.associateWith { beforeTracks[it].deepTimelineCopy() }
        val working = beforeTracks.map { it.deepTimelineCopy() }.toMutableList()
        val movingEntries = preview.placements.associateWith { placement ->
            working[placement.source.trackIndex].removeRegularEntry(placement.source.entryStartMs)
                ?: return TimelineCommandResult()
        }

        preview.placements.groupBy(Placement::targetTrackIndex).forEach { (trackIndex, placements) ->
            val occupied = mergeIntervals(placements.map { it.targetStartMs until it.targetEndMs })
            when (val target = working[trackIndex]) {
                is AudioTimelineTrack -> {
                    val survivors = target.entries.values.flatMap { entry ->
                        subtract(entry.startTimeMs until entry.endTimeMs, occupied).mapNotNull { interval ->
                            entry.buildSegment(interval.first, interval.exclusiveEnd)
                        }
                    }
                    target.entries.clear()
                    survivors.forEach { target.entries[it.startTimeMs] = it }
                }

                is MidiTimelineTrack -> {
                    val survivors = target.entries.values.flatMap { entry ->
                        subtract(entry.startTimeMs until entry.endTimeMs, occupied).mapNotNull { interval ->
                            entry.buildSegment(interval.first, interval.exclusiveEnd)
                        }
                    }
                    target.entries.clear()
                    survivors.forEach { target.entries[it.startTimeMs] = it }
                }
            }
        }

        preview.placements.forEach { placement ->
            when (val entry = movingEntries.getValue(placement)) {
                is AudioEntry -> (working[placement.targetTrackIndex] as AudioTimelineTrack)
                    .entries[placement.targetStartMs] = entry.copyWithShiftedStartMs(placement.targetStartMs)
                is MidiEntry -> (working[placement.targetTrackIndex] as MidiTimelineTrack)
                    .entries[placement.targetStartMs] = entry.copy(startTimeMs = placement.targetStartMs)
            }
        }

        val afterSnapshots = touchedIndices.associateWith { working[it].deepTimelineCopy() }
        val beforeSelections = SelectionManager.selections.value
        val movedSources = preview.placements.mapTo(mutableSetOf(), Placement::source)
        val retainedSelections = beforeSelections.filterNot { selection ->
            selection is Selectable.TimelineEntryItem && selection.clipId == null &&
                TimelineClipKey(selection.trackIndex, selection.entryStartMs) in movedSources
        }
        val afterSelections = retainedSelections + preview.placements.map {
            Selectable.TimelineEntryItem(it.targetTrackIndex, it.targetStartMs)
        }

        TimelineRepository.updateTracksSnapshot(working)
        SelectionManager.replaceSelections(afterSelections)
        UndoManager.addAction(
            UndoableAction.TimelineMultiTrackChange(
                beforeTracks = beforeSnapshots,
                afterTracks = afterSnapshots,
                beforeSelections = beforeSelections,
                afterSelections = afterSelections,
            )
        )
        return TimelineCommandResult(didChange = true)
    }

    private fun TimelineTrack<*>.regularEntry(startMs: Long) = when (this) {
        is AudioTimelineTrack -> entries[startMs]
        is MidiTimelineTrack -> entries[startMs]
        else -> null
    }

    private fun TimelineTrack<*>.removeRegularEntry(startMs: Long) = when (this) {
        is AudioTimelineTrack -> entries.remove(startMs)
        is MidiTimelineTrack -> entries.remove(startMs)
        else -> null
    }

    private fun TimelineTrack<*>.deepTimelineCopy(): TimelineTrack<*> = when (this) {
        is AudioTimelineTrack -> copyWithEntries(entries.mapValues { it.value.copy() })
        is MidiTimelineTrack -> copyWithEntries(
            entriesToCopy = entries.mapValues { it.value.copy(notes = it.value.notes.map { note -> note.copy() }) },
            chainEffectsToCopy = chainEffectEntries.mapValues { it.value.deepCopy() },
        )
        else -> error("Unsupported timeline track type: ${this::class}")
    }

    private fun mergeIntervals(intervals: List<LongRange>): List<LongRange> {
        val sorted = intervals.filter { !it.isEmpty() }.sortedBy(LongRange::first)
        if (sorted.isEmpty()) return emptyList()
        val merged = mutableListOf(sorted.first())
        sorted.drop(1).forEach { next ->
            val current = merged.last()
            if (next.first <= current.exclusiveEnd) {
                merged[merged.lastIndex] = current.first until maxOf(current.exclusiveEnd, next.exclusiveEnd)
            } else {
                merged += next
            }
        }
        return merged
    }

    private fun subtract(source: LongRange, cuts: List<LongRange>): List<LongRange> {
        var remaining = listOf(source)
        cuts.forEach { cut ->
            remaining = remaining.flatMap { segment ->
                if (cut.first >= segment.exclusiveEnd || cut.exclusiveEnd <= segment.first) {
                    listOf(segment)
                } else {
                    listOfNotNull(
                        (segment.first until minOf(cut.first, segment.exclusiveEnd)).takeUnless(LongRange::isEmpty),
                        (maxOf(cut.exclusiveEnd, segment.first) until segment.exclusiveEnd).takeUnless(LongRange::isEmpty),
                    )
                }
            }
        }
        return remaining
    }

    private fun MidiEntry.buildSegment(segmentStartMs: Long, segmentEndMs: Long): MidiEntry? {
        if (segmentEndMs <= segmentStartMs) return null
        val relativeStart = segmentStartMs - startTimeMs
        val relativeEnd = segmentEndMs - startTimeMs
        val segmentNotes = notes.mapNotNull { note ->
            val clippedStart = maxOf(note.startTimeMs, relativeStart)
            val clippedEnd = minOf(note.startTimeMs + note.durationMs, relativeEnd)
            if (clippedEnd <= clippedStart) null else note.copy(
                startTimeMs = clippedStart - relativeStart,
                durationMs = clippedEnd - clippedStart,
            )
        }
        return copy(
            startTimeMs = segmentStartMs,
            durationMs = segmentEndMs - segmentStartMs,
            notes = segmentNotes,
        )
    }

    private val LongRange.exclusiveEnd: Long
        get() = last + 1L
}
