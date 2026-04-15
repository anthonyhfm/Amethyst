package dev.anthonyhfm.amethyst.core.controls.automapping

import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.sampleChainStateFromAudioEntry
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDeviceState
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.clippedToRange
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain

fun buildChainDeviceFromTimelineAudioEntry(
    entry: AudioEntry,
    volumeAutomationLane: TimelineAutomationLane? = null
): GenericChainDevice<*>? {
    return sampleChainStateFromAudioEntry(
        entry = entry,
        volumeAutomationLane = volumeAutomationLane
    )?.let(StateChain::unpackDevice)
}

fun buildChainDevicesFromTimelineAudioRange(
    entries: List<AudioEntry>,
    automationLanes: List<TimelineAutomationLane>,
    rangeStartMs: Long
): List<GenericChainDevice<*>> {
    val volumeLane = automationLanes.firstOrNull { lane ->
        lane.target == TimelineTrackAutomationTarget.VOLUME
    }

    return entries.mapNotNull { entry ->
        val clippedVolumeLane = volumeLane?.clippedToRange(
            startMs = (entry.startTimeMs - rangeStartMs).coerceAtLeast(0L),
            endMs = ((entry.startTimeMs - rangeStartMs) + entry.durationMs).coerceAtLeast(0L),
            baseValue = volumeLane.valueAt(
                timeMs = (entry.startTimeMs - rangeStartMs).coerceAtLeast(0L),
                defaultValue = TimelineTrackAutomationTarget.VOLUME.defaultValue
            )
        )
        buildChainDeviceFromTimelineAudioEntry(
            entry = entry,
            volumeAutomationLane = clippedVolumeLane
        )
    }
}

fun buildChainDeviceFromTimelineMidiEntry(entry: MidiEntry): GenericChainDevice<*> {
    return StateChain.unpackDevice(
        PianoRollChainDeviceState(
            midiEntry = entry.copy(),
        )
    )
}

fun buildChainDeviceFromAutomappingClip(
    clip: AutomappingSelectedClip,
): GenericChainDevice<*>? {
    return when (clip) {
        is AutomappingSelectedClip.Audio -> buildChainDeviceFromTimelineAudioEntry(clip.entry)
        is AutomappingSelectedClip.Midi -> buildChainDeviceFromTimelineMidiEntry(clip.entry)
    }
}
