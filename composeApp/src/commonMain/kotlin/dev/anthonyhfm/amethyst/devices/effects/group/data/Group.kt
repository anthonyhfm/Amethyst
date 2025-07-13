package dev.anthonyhfm.amethyst.devices.effects.group.data

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Group(
    val name: String,
    @Transient
    val chain: Chain = Chain(),
    val stateChain: StateChain = StateChain.pack(chain)
) {

}