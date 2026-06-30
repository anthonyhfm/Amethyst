package dev.anthonyhfm.amethyst.core.controls.automapping

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode

data class AutomappingTarget(
    val parentDeviceSelectionUUID: String,
)

enum class AutomappingChainDomain {
    Lights,
    Sampling,
}

sealed interface AutomappingSelectedClip {
    val trackIndex: Int
    val entryStartMs: Long
    val displayName: String

    data class Audio(
        override val trackIndex: Int,
        override val entryStartMs: Long,
        val entry: AudioEntry,
    ) : AutomappingSelectedClip {
        override val displayName: String
            get() = entry.name.ifBlank { entry.fileName }
    }

    data class Midi(
        override val trackIndex: Int,
        override val entryStartMs: Long,
        val entry: MidiEntry,
    ) : AutomappingSelectedClip {
        override val displayName: String
            get() = entry.name
    }
}

fun resolveAutomappingChainDomain(
    mode: WorkspaceMode,
): AutomappingChainDomain? {
    return when (mode) {
        is LightsChainWorkspaceMode -> AutomappingChainDomain.Lights
        is SamplingChainWorkspaceMode -> AutomappingChainDomain.Sampling
        else -> null
    }
}

fun resolveAutomappingSelectedClip(
    mode: WorkspaceMode,
    tracks: List<TimelineTrack<*>>,
    selections: List<Selectable>,
): AutomappingSelectedClip? {
    val domain = resolveAutomappingChainDomain(mode) ?: return null
    return resolveAutomappingSelectedClip(domain, tracks, selections)
}

fun resolveAutomappingSelectedClip(
    domain: AutomappingChainDomain,
    tracks: List<TimelineTrack<*>>,
    selections: List<Selectable>,
): AutomappingSelectedClip? {
    val selectedEntry = selections
        .filterIsInstance<Selectable.TimelineEntryItem>()
        .distinctBy { it.trackIndex to it.entryStartMs }
        .singleOrNull()
        ?: return null

    return when (domain) {
        AutomappingChainDomain.Lights -> {
            val track = tracks.getOrNull(selectedEntry.trackIndex) as? MidiTimelineTrack
                ?: return null
            val entry = track.entries[selectedEntry.entryStartMs] ?: return null
            AutomappingSelectedClip.Midi(
                trackIndex = selectedEntry.trackIndex,
                entryStartMs = entry.startTimeMs,
                entry = entry,
            )
        }

        AutomappingChainDomain.Sampling -> {
            val track = tracks.getOrNull(selectedEntry.trackIndex) as? AudioTimelineTrack
                ?: return null
            val entry = track.entries[selectedEntry.entryStartMs] ?: return null
            AutomappingSelectedClip.Audio(
                trackIndex = selectedEntry.trackIndex,
                entryStartMs = entry.startTimeMs,
                entry = entry,
            )
        }
    }
}
