package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationPoint

sealed interface TimelineEditCommand {
    data class DeleteTracks(
        val trackIndices: List<Int>
    ) : TimelineEditCommand

    data class DuplicateTracks(
        val trackIndices: List<Int>
    ) : TimelineEditCommand

    data class RenameTrack(
        val trackIndex: Int,
        val newName: String
    ) : TimelineEditCommand

    data class DeleteEntries(
        val trackIndex: Int,
        val entryStartTimes: List<Long>
    ) : TimelineEditCommand

    data class DuplicateEntries(
        val trackIndex: Int,
        val entryStartTimes: List<Long>
    ) : TimelineEditCommand

    data class RenameEntry(
        val trackIndex: Int,
        val entryStartTime: Long,
        val newName: String
    ) : TimelineEditCommand

    data class DeleteRange(
        val trackIndex: Int,
        val startMs: Long,
        val endMs: Long
    ) : TimelineEditCommand

    data class DuplicateRange(
        val trackIndex: Int,
        val startMs: Long,
        val endMs: Long
    ) : TimelineEditCommand

    data class CreateNotes(
        val trackIndex: Int,
        val entryStartTime: Long,
        val notes: List<MidiNote>
    ) : TimelineEditCommand

    data class MoveNotes(
        val trackIndex: Int,
        val entryStartTime: Long,
        val changes: List<TimelineEditedNote>
    ) : TimelineEditCommand

    data class ResizeNotes(
        val trackIndex: Int,
        val entryStartTime: Long,
        val changes: List<TimelineEditedNote>
    ) : TimelineEditCommand

    data class UpdateNotes(
        val trackIndex: Int,
        val entryStartTime: Long,
        val changes: List<TimelineEditedNote>
    ) : TimelineEditCommand

    data class DeleteNotes(
        val trackIndex: Int,
        val entryStartTime: Long,
        val notes: List<MidiNote>
    ) : TimelineEditCommand

    data class CreateAutomationPoints(
        val trackIndex: Int,
        val lane: TimelineAutomationLaneKey,
        val points: List<TimelineAutomationPoint>
    ) : TimelineEditCommand

    data class MoveAutomationPoints(
        val trackIndex: Int,
        val lane: TimelineAutomationLaneKey,
        val changes: List<TimelineEditedAutomationPoint>
    ) : TimelineEditCommand

    data class DeleteAutomationPoints(
        val trackIndex: Int,
        val lane: TimelineAutomationLaneKey,
        val pointIds: List<String>
    ) : TimelineEditCommand

    data class SetAutomationLaneVisibility(
        val trackIndex: Int,
        val lane: TimelineAutomationLaneKey,
        val visible: Boolean
    ) : TimelineEditCommand

    data class SetAutomationLaneEnabled(
        val trackIndex: Int,
        val lane: TimelineAutomationLaneKey,
        val enabled: Boolean
    ) : TimelineEditCommand
}

data class TimelineEditedNote(
    val before: MidiNote,
    val after: MidiNote
)

data class TimelineEditedAutomationPoint(
    val before: TimelineAutomationPoint,
    val after: TimelineAutomationPoint
)

data class TimelineCreatedEntry(
    val trackIndex: Int,
    val entryStartMs: Long
)

data class TimelineCommandResult(
    val didChange: Boolean = false,
    val createdEntries: List<TimelineCreatedEntry> = emptyList()
) {
    operator fun plus(other: TimelineCommandResult): TimelineCommandResult {
        return TimelineCommandResult(
            didChange = didChange || other.didChange,
            createdEntries = createdEntries + other.createdEntries
        )
    }
}
