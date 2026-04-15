package dev.anthonyhfm.amethyst.core.controls.selection

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext
import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipKey
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.timeline.data.MidiNote // hinzugefügt für PianoRollNote

interface Selectable {
    val selectionUUID: String

    data class VirtualViewportDevice(
        val element: LaunchpadViewportElement,
        override val selectionUUID: String = element.selectionUUID
    ) : Selectable

    data class ChainDevice(
        val parent: Chain,
        val device: dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>,
        override val selectionUUID: String = device.selectionUUID
    ) : Selectable

    data class GradientStep(
        val parent: GradientChainDevice,
        val stepIndex: Int,
        override val selectionUUID: String = parent.state.value.gradientData[stepIndex].selectionUUID
    ) : Selectable

    data class GroupChainItem(
        val parent: dev.anthonyhfm.amethyst.devices.GenericChainDevice<*>,
        val groupIndex: Int,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class KeyframeItem(
        val parent: dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice,
        val frameIndex: Int,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class TimelineTime(
        val trackIndex: Int,
        val timeMs: Long,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class TimelineEntryItem(
        val trackIndex: Int,
        val entryStartMs: Long,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class PianoRollNote(
        val trackIndex: Int,
        val entryStartMs: Long,
        val note: MidiNote,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class TimelineRange(
        val trackIndex: Int,
        val startMs: Long,
        val endMs: Long,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class TimelineTrack(
        val trackIndex: Int,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class TimelineAutomationLane(
        val trackIndex: Int,
        val target: TimelineTrackAutomationTarget,
        val bindingId: String? = null,
        override val selectionUUID: String = buildSelectionUUID(trackIndex, target, bindingId)
    ) : Selectable {
        val laneKey: TimelineAutomationLaneKey
            get() = TimelineAutomationLaneKey(
                target = target,
                bindingId = bindingId
            ).normalized()

        companion object {
            private fun buildSelectionUUID(
                trackIndex: Int,
                target: TimelineTrackAutomationTarget,
                bindingId: String?
            ): String {
                val normalizedBindingId = target.normalizeBindingId(bindingId) ?: "_"
                return "timeline-automation-lane:$trackIndex:${target.name}:$normalizedBindingId"
            }
        }
    }

    data class TimelineAutomationPoint(
        val trackIndex: Int,
        val target: TimelineTrackAutomationTarget,
        val bindingId: String? = null,
        val pointId: String,
        override val selectionUUID: String = buildSelectionUUID(trackIndex, target, bindingId, pointId)
    ) : Selectable {
        val laneKey: TimelineAutomationLaneKey
            get() = TimelineAutomationLaneKey(
                target = target,
                bindingId = bindingId
            ).normalized()

        companion object {
            private fun buildSelectionUUID(
                trackIndex: Int,
                target: TimelineTrackAutomationTarget,
                bindingId: String?,
                pointId: String
            ): String {
                val normalizedBindingId = target.normalizeBindingId(bindingId) ?: "_"
                return "timeline-automation-point:$trackIndex:${target.name}:$normalizedBindingId:$pointId"
            }
        }
    }
}

fun Selectable.TimelineEntryItem.toClipKey(): TimelineClipKey {
    return TimelineClipKey(trackIndex = trackIndex, entryStartMs = entryStartMs)
}

fun Selectable.PianoRollNote.toClipKey(): TimelineClipKey {
    return TimelineClipKey(trackIndex = trackIndex, entryStartMs = entryStartMs)
}

fun TimelineClipKey.toTimelineEntrySelection(): Selectable.TimelineEntryItem {
    return Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = entryStartMs)
}

fun TimelineClipContext.toTimelineEntrySelection(): Selectable.TimelineEntryItem {
    return clipKey.toTimelineEntrySelection()
}
