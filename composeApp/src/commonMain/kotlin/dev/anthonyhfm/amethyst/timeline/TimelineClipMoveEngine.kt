package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineEntry
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
        val anchorEntry = anchorTrack.entryAt(key = command.anchorKey) ?: return Preview(false)
        val trackDelta = command.targetTrackIndex - command.anchorKey.trackIndex
        val timeDelta = command.targetStartMs.coerceAtLeast(0L) - anchorEntry.startTimeMs
        val uniqueKeys = command.entryKeys.distinct().ifEmpty { listOf(command.anchorKey) }

        val placements = uniqueKeys.map { key ->
            val sourceTrack = tracks.getOrNull(key.trackIndex) ?: return Preview(false)
            if (sourceTrack::class != anchorTrack::class) return@map null
            val entry = sourceTrack.entryAt(key = key) ?: return Preview(false)
            if (entry::class != anchorEntry::class) {
                return@map null
            }
            val targetTrackIndex = key.trackIndex + trackDelta
            val targetTrack = tracks.getOrNull(targetTrackIndex) ?: return Preview(false)
            if (targetTrack::class != sourceTrack::class) return Preview(false)
            val targetStart = entry.startTimeMs + timeDelta
            if (targetStart < 0L) return Preview(false)
            Placement(key, targetTrackIndex, targetStart, entry.durationMs)
        }.filterNotNull()

        if (placements.none { it.source == command.anchorKey }) return Preview(false)
        if (placements.isEmpty()) return Preview(false)

        val movingEffectIds = if (command.copy) emptySet() else placements.mapNotNull { it.source.clipId }.toSet()
        val blockedByChainEffect = placements.any { placement ->
            val target = tracks[placement.targetTrackIndex] as? MidiTimelineTrack ?: return@any false
            val overlapsEffect = target.chainEffectEntries.values.any { effect ->
                effect.clipId !in movingEffectIds &&
                    effect.startTimeMs < placement.targetEndMs && effect.endTimeMs > placement.targetStartMs
            }
            val overlapsRegular = placement.source.clipId != null && target.entries.values.any { entry ->
                entry.startTimeMs < placement.targetEndMs && entry.endTimeMs > placement.targetStartMs
            }
            overlapsEffect || overlapsRegular
        }
        val blockedCopy = command.copy && placements.any { placement ->
            val entries = when (val target = tracks[placement.targetTrackIndex]) {
                is AudioTimelineTrack -> target.entries.values
                is MidiTimelineTrack -> target.entries.values
                else -> emptyList()
            }
            entries.any { it.startTimeMs < placement.targetEndMs && it.endTimeMs > placement.targetStartMs }
        }
        return Preview(isValid = !blockedByChainEffect && !blockedCopy, placements = placements)
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
            val source = working[placement.source.trackIndex]
            if (command.copy) {
                source.entryAt(key = placement.source)
            } else {
                source.removeEntryAt(key = placement.source)
            } ?: return TimelineCommandResult()
        }

        preview.placements.groupBy(Placement::targetTrackIndex).forEach { (trackIndex, placements) ->
            if (placements.all { it.source.clipId != null }) {
                return@forEach
            }
            val occupied = mergeIntervals(placements.map { it.targetStartMs until it.targetEndMs })
            if (command.copy && placements.any { placement ->
                    working[trackIndex].let { track ->
                        when (track) {
                            is AudioTimelineTrack -> track.entries.values
                            is MidiTimelineTrack -> track.entries.values
                            else -> emptyList()
                        }
                    }.any { entry ->
                        entry.startTimeMs < placement.targetEndMs && entry.endTimeMs > placement.targetStartMs
                    }
                }
            ) {
                return TimelineCommandResult()
            }
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

        val placedEffectIds = mutableMapOf<Placement, String>()
        preview.placements.forEach { placement ->
            when (val entry = movingEntries.getValue(placement)) {
                is ChainEffectEntry -> {
                    val placed = entry.deepCopy(
                        clipId = if (command.copy) UUID.randomUUID() else entry.clipId,
                        startTimeMs = placement.targetStartMs,
                    )
                    (working[placement.targetTrackIndex] as MidiTimelineTrack).chainEffectEntries[placement.targetStartMs] = placed
                    placedEffectIds[placement] = placed.clipId
                }
                is AudioEntry -> (working[placement.targetTrackIndex] as AudioTimelineTrack)
                    .entries[placement.targetStartMs] = entry.copyWithShiftedStartMs(placement.targetStartMs)
                is MidiEntry -> (working[placement.targetTrackIndex] as MidiTimelineTrack)
                    .entries[placement.targetStartMs] = entry.copy(
                        startTimeMs = placement.targetStartMs,
                        notes = if (command.copy) {
                            entry.notes.map { it.copy(noteId = UUID.randomUUID()) }
                        } else {
                            entry.notes
                        },
                    )
            }
        }

        val afterSnapshots = touchedIndices.associateWith { working[it].deepTimelineCopy() }
        val beforeSelections = SelectionManager.selections.value
        val movedSources = preview.placements.mapTo(mutableSetOf(), Placement::source)
        val retainedSelections = beforeSelections.filterNot { selection ->
            selection is Selectable.TimelineEntryItem &&
                TimelineClipKey(trackIndex = selection.trackIndex, entryStartMs = selection.entryStartMs, clipId = selection.clipId) in movedSources
        }
        val afterSelections = retainedSelections + preview.placements.map {
            Selectable.TimelineEntryItem(trackIndex = it.targetTrackIndex, entryStartMs = it.targetStartMs, clipId = placedEffectIds[it])
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

    private fun TimelineTrack<*>.entryAt(key: TimelineClipKey): TimelineEntry? = when (this) {
        is AudioTimelineTrack -> entries[key.entryStartMs]
        is MidiTimelineTrack -> if (key.clipId == null) {
            entries[key.entryStartMs]
        } else {
            chainEffectEntries.values.firstOrNull { it.clipId == key.clipId }
        }
        else -> null
    }

    private fun TimelineTrack<*>.removeEntryAt(key: TimelineClipKey): TimelineEntry? = when (this) {
        is AudioTimelineTrack -> entries.remove(key = key.entryStartMs)
        is MidiTimelineTrack -> if (key.clipId == null) {
            entries.remove(key = key.entryStartMs)
        } else {
            val entry = chainEffectEntries.values.firstOrNull { it.clipId == key.clipId }
            entry?.let { chainEffectEntries.remove(key = it.startTimeMs) }
        }
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
