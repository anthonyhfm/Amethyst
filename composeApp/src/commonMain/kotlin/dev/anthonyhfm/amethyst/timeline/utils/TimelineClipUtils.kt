package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack

object TimelineClipUtils {
    fun cutAtSelection(selection: Selectable.TimelineTime): Boolean {
        val trackIndex = selection.trackIndex
        val cutTimeMs = selection.timeMs
        val tracks = TimelineRepository.tracks.value
        if (trackIndex !in tracks.indices) return false
        val track = tracks[trackIndex]
        
        return when (track) {
            is AudioTimelineTrack -> cutAudioAtSelection(trackIndex, track, cutTimeMs)
            is MidiTimelineTrack, is LightsTimelineTrack -> cutMidiAtSelection(trackIndex, track, cutTimeMs)
            else -> false
        }
    }

    private fun cutAudioAtSelection(trackIndex: Int, track: AudioTimelineTrack, cutTimeMs: Long): Boolean {
        val clip = track.entries.values.firstOrNull { cutTimeMs > it.startTimeMs && cutTimeMs < it.endTimeMs } ?: return false
        val relativeOffsetMs = cutTimeMs - clip.startTimeMs
        if (relativeOffsetMs <= 0 || relativeOffsetMs >= clip.durationMs) return false

        val rawData = clip.rawData
        val bytesPerSample = (clip.bitDepth / 8) * clip.channels
        val samplesPerMs = clip.sampleRate.toDouble() / 1000.0
        val samplesToSplitExact = relativeOffsetMs * samplesPerMs
        val samplesToSplit = kotlin.math.round(samplesToSplitExact).toLong()
        val bytesToSplit = (samplesToSplit * bytesPerSample).toInt()
        val alignedBytesToSplit = bytesPerSample * (bytesToSplit / bytesPerSample)

        if (rawData == null || rawData.isEmpty() || alignedBytesToSplit <= 0 || alignedBytesToSplit >= rawData.size) {
            val firstDuration = relativeOffsetMs
            val secondDuration = clip.durationMs - relativeOffsetMs
            if (secondDuration <= 0) return false
            val first = clip.copy(startTimeMs = clip.startTimeMs, durationMs = firstDuration, rawData = rawData)
            val second = clip.copy(startTimeMs = cutTimeMs, durationMs = secondDuration, rawData = rawData)
            replaceAudioClip(trackIndex, clip, first, second)
            return true
        }

        val firstPart = rawData.sliceArray(0 until alignedBytesToSplit)
        val secondPart = rawData.sliceArray(alignedBytesToSplit until rawData.size)
        val samplesFirst = firstPart.size / bytesPerSample
        val samplesSecond = secondPart.size / bytesPerSample
        val durationFirstMsCalc = (samplesFirst / samplesPerMs).toLong()
        var durationSecondMsCalc = (samplesSecond / samplesPerMs).toLong()
        val totalCalc = durationFirstMsCalc + durationSecondMsCalc
        val delta = clip.durationMs - totalCalc
        if (delta != 0L) durationSecondMsCalc = (durationSecondMsCalc + delta).coerceAtLeast(1L)
        if (durationFirstMsCalc <= 0 || durationSecondMsCalc <= 0) return false

        val first = AudioEntry(
            startTimeMs = clip.startTimeMs,
            durationMs = durationFirstMsCalc,
            fileName = clip.fileName,
            rawData = firstPart,
            sampleRate = clip.sampleRate,
            channels = clip.channels,
            bitDepth = clip.bitDepth
        )
        val second = AudioEntry(
            startTimeMs = cutTimeMs,
            durationMs = durationSecondMsCalc,
            fileName = clip.fileName,
            rawData = secondPart,
            sampleRate = clip.sampleRate,
            channels = clip.channels,
            bitDepth = clip.bitDepth
        )
        replaceAudioClip(trackIndex, clip, first, second)
        return true
    }

    private fun cutMidiAtSelection(trackIndex: Int, track: TimelineTrack<*>, cutTimeMs: Long): Boolean {
        val midiTrack = track as TimelineTrack<MidiEntry>
        val clip = midiTrack.entries.values.firstOrNull { cutTimeMs > it.startTimeMs && cutTimeMs < it.endTimeMs } ?: return false
        val relativeOffsetMs = cutTimeMs - clip.startTimeMs
        if (relativeOffsetMs <= 0 || relativeOffsetMs >= clip.durationMs) return false

        // Split notes based on cut time
        val notesInFirst = mutableListOf<dev.anthonyhfm.amethyst.timeline.data.MidiNote>()
        val notesInSecond = mutableListOf<dev.anthonyhfm.amethyst.timeline.data.MidiNote>()
        
        clip.notes.forEach { note ->
            val noteStart = note.startTimeMs
            val noteEnd = note.startTimeMs + note.durationMs
            
            when {
                // Note entirely in first clip
                noteEnd <= relativeOffsetMs -> notesInFirst.add(note)
                // Note entirely in second clip
                noteStart >= relativeOffsetMs -> {
                    // Adjust note timing relative to second clip start
                    notesInSecond.add(note.copy(startTimeMs = noteStart - relativeOffsetMs))
                }
                // Note spans across cut - split it
                else -> {
                    // Add truncated note to first clip
                    notesInFirst.add(note.copy(durationMs = relativeOffsetMs - noteStart))
                    // Add remaining part to second clip
                    notesInSecond.add(note.copy(
                        startTimeMs = 0,
                        durationMs = noteEnd - relativeOffsetMs
                    ))
                }
            }
        }

        val firstDuration = relativeOffsetMs
        val secondDuration = clip.durationMs - relativeOffsetMs
        
        val first = clip.copy(
            startTimeMs = clip.startTimeMs,
            durationMs = firstDuration,
            notes = notesInFirst
        )
        val second = clip.copy(
            startTimeMs = cutTimeMs,
            durationMs = secondDuration,
            notes = notesInSecond
        )
        
        replaceMidiClip(trackIndex, clip, first, second, track is LightsTimelineTrack)
        return true
    }

    private fun replaceAudioClip(trackIndex: Int, original: AudioEntry, first: AudioEntry, second: AudioEntry) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val audioTrack = currentTracks[trackIndex] as? AudioTimelineTrack ?: return
        audioTrack.entries.remove(original.startTimeMs)
        audioTrack.entries[first.startTimeMs] = first
        audioTrack.entries[second.startTimeMs] = second
        val newTrack = AudioTimelineTrack().apply { entries.putAll(audioTrack.entries) }
        currentTracks[trackIndex] = newTrack
        TimelineRepository.tracks.value = currentTracks.toList()
        // Undo Action hinzufügen
        UndoManager.addAction(
            UndoableAction.TimelineClipSplit(
                trackIndex = trackIndex,
                original = original,
                left = first,
                right = second
            )
        )
    }

    private fun replaceMidiClip(trackIndex: Int, original: MidiEntry, first: MidiEntry, second: MidiEntry, isLightsTrack: Boolean) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val track = currentTracks[trackIndex]
        if (track !is MidiTimelineTrack && track !is LightsTimelineTrack) return
        
        val midiTrack = track as TimelineTrack<MidiEntry>
        midiTrack.entries.remove(original.startTimeMs)
        midiTrack.entries[first.startTimeMs] = first
        midiTrack.entries[second.startTimeMs] = second
        
        val newTrack = if (isLightsTrack) {
            LightsTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        } else {
            MidiTimelineTrack().apply { entries.putAll(midiTrack.entries) }
        }
        currentTracks[trackIndex] = newTrack
        TimelineRepository.tracks.value = currentTracks.toList()
        
        // Add undo action
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
