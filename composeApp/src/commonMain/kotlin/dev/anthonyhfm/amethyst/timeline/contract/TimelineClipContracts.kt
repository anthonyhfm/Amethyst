package dev.anthonyhfm.amethyst.timeline.contract

import dev.anthonyhfm.amethyst.timeline.data.MidiEntry

enum class TimelineClipKind {
    AUDIO,
    MIDI
}

data class TimelineClipKey(
    val trackIndex: Int,
    val entryStartMs: Long
)

data class TimelineClipContext(
    val clipKey: TimelineClipKey,
    val kind: TimelineClipKind
) {
    val trackIndex: Int
        get() = clipKey.trackIndex

    val entryStartMs: Long
        get() = clipKey.entryStartMs

    val isNoteCapable: Boolean
        get() = kind == TimelineClipKind.MIDI

    fun withEntryStart(entryStartMs: Long): TimelineClipContext {
        return copy(clipKey = clipKey.copy(entryStartMs = entryStartMs))
    }

    companion object {
        fun midi(trackIndex: Int, entry: MidiEntry): TimelineClipContext {
            return TimelineClipContext(
                clipKey = TimelineClipKey(
                    trackIndex = trackIndex,
                    entryStartMs = entry.startTimeMs
                ),
                kind = TimelineClipKind.MIDI
            )
        }
    }
}
