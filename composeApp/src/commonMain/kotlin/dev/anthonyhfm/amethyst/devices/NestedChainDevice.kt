package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.core.engine.elements.Chain

interface NestedChainDevice {
    fun nestedChains(): List<Chain>

    /** Child chains that own audio buses; trigger-only preprocess chains may opt out. */
    fun audioNestedChains(): List<Chain> = nestedChains()
}

/** Stable depth-first device order shared by target pickers and graph-adjacent tooling. */
fun Chain.devicesDepthFirst(): List<GenericChainDevice<*>> = buildList {
    val visited = mutableSetOf<Chain>()

    fun visit(chain: Chain) {
        if (!visited.add(chain)) return
        chain.devices.value.forEach { device ->
            add(device)
            (device as? NestedChainDevice)?.nestedChains()?.forEach(::visit)
        }
    }

    visit(this@devicesDepthFirst)
}
