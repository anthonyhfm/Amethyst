package dev.anthonyhfm.amethyst.core.controls.selection

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

interface Selectable {
    val selectionUUID: String

    data class VirtualViewportDevice(
        val element: LaunchpadViewportElement,
        override val selectionUUID: String = element.selectionUUID
    ) : Selectable

    data class ChainDevice(
        val parent: Chain,
        val device: dev.anthonyhfm.amethyst.devices.ChainDevice<*>,
        override val selectionUUID: String = device.selectionUUID
    ) : Selectable
}