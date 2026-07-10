package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface CompositionNodeState

@Serializable
@SerialName("scanner")
data class ScannerNodeState(
    val angleDegrees: Float = 0f,
    val thickness: Float = 0.5f,
    val red: Float = 1f,
    val green: Float = 1f,
    val blue: Float = 1f,
) : CompositionNodeState

@Serializable
@SerialName("rotate")
data class RotateNodeState(
    val angleDegrees: Float = 0f,
) : CompositionNodeState

@Serializable
@SerialName("mirror")
data class MirrorNodeState(
    val angleDegrees: Float = 90f,
) : CompositionNodeState

@Serializable
@SerialName("symmetry")
data class SymmetryNodeState(
    val mode: String = "mirror-half", // "mirror-half", "quad-mirror", "quad-pinwheel"
    val axis: String = "horizontal", // "horizontal", "vertical"
    val sourceAnchor: String = "bl", // "bl", "br", "tr", "tl"
) : CompositionNodeState

@Serializable
@SerialName("output")
data object OutputNodeState : CompositionNodeState
