package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.copyWithPreciseTiming
import dev.anthonyhfm.amethyst.timeline.data.endTimeUs
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import dev.anthonyhfm.amethyst.timeline.data.usToSamples

object TimelineClipUtils {
    fun cutAtSelection(selection: Selectable.TimelineTime): Boolean {
        val trackIndex = selection.trackIndex
        val cutTimeMs = selection.timeMs
        val tracks = TimelineRepository.tracks.value
        if (trackIndex !in tracks.indices) return false
        val track = tracks[trackIndex]

        return when (track) {
            is AudioTimelineTrack -> cutAudioAtSelection(trackIndex, track, cutTimeMs)
            is MidiTimelineTrack -> cutMidiAtSelection(trackIndex, track, cutTimeMs)
            else -> false
        }
    }

    // ── Audio cut ─────────────────────────────────────────────────────────────

    /**
     * Non-destructive cut: only [AudioEntry.clipStartSample]/[AudioEntry.clipEndSample] change.
     * The [AudioSource] PCM bytes are never copied or modified.
     */
    private fun cutAudioAtSelection(trackIndex: Int, track: AudioTimelineTrack, cutTimeMs: Long): Boolean {
        val clip = track.entries.values.firstOrNull { cutTimeMs > it.startTimeMs && cutTimeMs < it.endTimeMs }
            ?: return false
        val cutTimeUs = msToUs(cutTimeMs)
        if (cutTimeUs <= clip.startTimeUs || cutTimeUs >= clip.endTimeUs) return false

        val relativeCutUs = cutTimeUs - clip.startTimeUs
        val cutSample = (clip.clipStartSample + usToSamples(relativeCutUs, clip.sampleRate))
            .coerceIn(clip.clipStartSample + 1L, clip.clipEndSample - 1L)

        val leftSampleCount = cutSample - clip.clipStartSample
        val rightSampleCount = clip.clipEndSample - cutSample
        if (leftSampleCount <= 0L || rightSampleCount <= 0L) return false

        val left = clip.copyWithPreciseTiming(
            durationUs = cutTimeUs - clip.startTimeUs,
            clipEndSample = cutSample
        )
        val right = clip.copyWithPreciseTiming(
            startTimeUs = cutTimeUs,
            durationUs = clip.endTimeUs - cutTimeUs,
            clipStartSample = cutSample,
            clipEndSample = clip.clipEndSample
        )

        if (left.durationUs <= 0L || right.durationUs <= 0L) return false
        if (samplesToUs(leftSampleCount, clip.sampleRate) <= 0L || samplesToUs(rightSampleCount, clip.sampleRate) <= 0L) return false

        replaceAudioClip(trackIndex, clip, left, right)
        return true
    }

    // ── MIDI cut ──────────────────────────────────────────────────────────────

    private fun cutMidiAtSelection(trackIndex: Int, track: TimelineTrack<*>, cutTimeMs: Long): Boolean {
        val midiTrack = track as TimelineTrack<MidiEntry>
        val clip = midiTrack.entries.values.firstOrNull { cutTimeMs > it.startTimeMs && cutTimeMs < it.endTimeMs }
            ?: return false
        val relativeOffsetMs = cutTimeMs - clip.startTimeMs
        if (relativeOffsetMs <= 0 || relativeOffsetMs >= clip.durationMs) return false

        val notesInFirst = mutableListOf<dev.anthonyhfm.amethyst.timeline.data.MidiNote>()
        val notesInSecond = mutableListOf<dev.anthonyhfm.amethyst.timeline.data.MidiNote>()

        clip.notes.forEach { note ->
            val noteStart = note.startTimeMs
            val noteEnd = note.startTimeMs + note.durationMs
            when {
                noteEnd <= relativeOffsetMs -> notesInFirst.add(note)
                noteStart >= relativeOffsetMs -> notesInSecond.add(note.copy(startTimeMs = noteStart - relativeOffsetMs))
                else -> {
                    notesInFirst.add(note.copy(durationMs = relativeOffsetMs - noteStart))
                    notesInSecond.add(note.copy(startTimeMs = 0, durationMs = noteEnd - relativeOffsetMs))
                }
            }
        }

        val first = clip.copy(startTimeMs = clip.startTimeMs, durationMs = relativeOffsetMs, notes = notesInFirst)
        val second = clip.copy(startTimeMs = cutTimeMs, durationMs = clip.durationMs - relativeOffsetMs, notes = notesInSecond)
        replaceMidiClip(trackIndex, clip, first, second)
        return true
    }

    // ── Replace helpers ───────────────────────────────────────────────────────

    private fun replaceAudioClip(trackIndex: Int, original: AudioEntry, first: AudioEntry, second: AudioEntry) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val audioTrack = currentTracks[trackIndex] as? AudioTimelineTrack ?: return
        audioTrack.entries.remove(original.startTimeMs)
        audioTrack.entries[first.startTimeMs] = first
        audioTrack.entries[second.startTimeMs] = second
        currentTracks[trackIndex] = audioTrack.copyWithEntries()
        TimelineRepository.tracks.value = currentTracks.toList()

        UndoManager.addAction(
            UndoableAction.TimelineClipSplit(
                trackIndex = trackIndex,
                original = original,
                left = first,
                right = second
            )
        )
    }

    private fun replaceMidiClip(trackIndex: Int, original: MidiEntry, first: MidiEntry, second: MidiEntry) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val track = currentTracks[trackIndex]
        if (track !is MidiTimelineTrack) return
        track.entries.remove(original.startTimeMs)
        track.entries[first.startTimeMs] = first
        track.entries[second.startTimeMs] = second
        currentTracks[trackIndex] = track.copyWithEntries()
        TimelineRepository.tracks.value = currentTracks.toList()

        UndoManager.addAction(
            UndoableAction.MidiTimelineClipSplit(
                trackIndex = trackIndex,
                original = original,
                left = first,
                right = second
            )
        )
    }
}
