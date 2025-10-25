package dev.anthonyhfm.amethyst.timeline.utils

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack

object TimelineClipUtils {
    fun cutAtSelection(selection: Selectable.TimelineTime): Boolean {
        val trackIndex = selection.trackIndex
        val cutTimeMs = selection.timeMs
        val tracks = TimelineRepository.tracks.value
        if (trackIndex !in tracks.indices) return false
        val track = tracks[trackIndex]
        if (track !is AudioTimelineTrack) return false

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
            replaceClip(trackIndex, clip, first, second)
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
        replaceClip(trackIndex, clip, first, second)
        return true
    }

    private fun replaceClip(trackIndex: Int, original: AudioEntry, first: AudioEntry, second: AudioEntry) {
        val currentTracks = TimelineRepository.tracks.value.toMutableList()
        val audioTrack = currentTracks[trackIndex] as? AudioTimelineTrack ?: return
        audioTrack.entries.remove(original.startTimeMs)
        audioTrack.entries[first.startTimeMs] = first
        audioTrack.entries[second.startTimeMs] = second
        val newTrack = AudioTimelineTrack().apply { entries.putAll(audioTrack.entries) }
        currentTracks[trackIndex] = newTrack
        TimelineRepository.tracks.value = currentTracks.toList()
    }
}
