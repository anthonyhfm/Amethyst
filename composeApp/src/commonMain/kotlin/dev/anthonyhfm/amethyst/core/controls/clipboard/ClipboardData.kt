package dev.anthonyhfm.amethyst.core.controls.clipboard

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry

sealed interface ClipboardData {
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

    data class TimelineEntries(
        val entries: List<AudioEntry>
    ) : ClipboardData

    data class MidiEntries(
        val entries: List<MidiEntry>,
        val isLightsTrack: Boolean
    ) : ClipboardData
}