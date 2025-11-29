package dev.anthonyhfm.amethyst.workspace.data

import kotlinx.serialization.Serializable

@Serializable
data class AutoPlayData(
    val actions: Map<Double, List<Action>>
) {
    @Serializable
    data class Action(
        val x: Int,
        val y: Int,
        val down: Boolean
    )
}