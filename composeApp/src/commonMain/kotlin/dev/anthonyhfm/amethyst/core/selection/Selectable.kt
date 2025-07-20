package dev.anthonyhfm.amethyst.core.selection

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID

interface Selectable {
    val selectionUUID: String

    data class VirtualViewportDevice(
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable

    data class ChainDevice(
        val parent: Chain,
        override val selectionUUID: String = UUID.randomUUID()
    ) : Selectable
}