package dev.anthonyhfm.amethyst.core.selection

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID

interface Selectable {
    val selectionUUID: String
        get() = UUID.randomUUID()

    data object VirtualViewportDevice : Selectable

    data class ChainDevice(
        val parent: Chain
    ) : Selectable
}