package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.core.engine.elements.Chain

interface NestedChainDevice {
    fun nestedChains(): List<Chain>
}
