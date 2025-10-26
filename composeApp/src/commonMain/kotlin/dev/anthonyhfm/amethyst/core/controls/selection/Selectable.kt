package dev.anthonyhfm.amethyst.core.controls.selection

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientChainDevice
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

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
}