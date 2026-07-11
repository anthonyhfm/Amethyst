package dev.anthonyhfm.amethyst.core.controls.clipboard

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionConnection
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.timeline.data.MidiNote

sealed interface ClipboardData {
    data class CompositionSubgraph(
        val nodes: List<CompositionNode>,
        val connections: List<CompositionConnection>,
    ) : ClipboardData

    data class ChainDevice(
        val states: List<DeviceState>,
        val type: ChainType
    ) : ClipboardData {
        enum class ChainType {
            Lights,
            Sampling
        }
    }

    data class GradientStep(
        val step: Selectable.GradientStep
    ) : ClipboardData

    data class Keyframe(
        val frames: List<Frame>
    ) : ClipboardData

    data class GroupChainItem(
        val groups: List<Group>
    ) : ClipboardData

    data class TimelineAudioEntries(
        val entries: List<AudioEntry>
    ) : ClipboardData

    data class TimelineAudioRange(
        val entries: List<AudioEntry>,
        val automationLanes: List<TimelineAutomationLane>,
        val rangeStartMs: Long,
        val rangeEndMs: Long
    ) : ClipboardData

    data class TimelineMidiEntries(
        val entries: List<MidiEntry>
    ) : ClipboardData

    data class PianoRollNotes(
        val notes: List<MidiNote>
    ) : ClipboardData

    data class TimelineTracks(
        val tracks: List<TimelineTrack<*>>,
    ) : ClipboardData
}
