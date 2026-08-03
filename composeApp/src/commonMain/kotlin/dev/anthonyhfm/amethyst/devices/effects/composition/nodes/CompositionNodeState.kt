package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import kotlinx.serialization.Serializable

@Serializable
sealed interface CompositionNodeState

/** Implemented by node states whose origin can be bound to the live trigger position. */
interface OriginBindableState {
    val boundToOrigin: Boolean
}
