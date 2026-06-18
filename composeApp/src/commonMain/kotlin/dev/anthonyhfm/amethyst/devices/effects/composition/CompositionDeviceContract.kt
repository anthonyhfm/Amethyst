package dev.anthonyhfm.amethyst.devices.effects.composition

import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.Serializable

sealed interface CompositionDeviceContract {

    sealed interface Event {
        data class AddNode(val type: String, val x: Float, val y: Float) : Event
        data class RemoveNode(val nodeId: String) : Event
        data class MoveNode(val nodeId: String, val x: Float, val y: Float) : Event
        data class UpdateNodeProperty(val nodeId: String, val key: String, val value: String) : Event
        data class AddConnection(
            val fromNodeId: String,
            val fromPinId: String,
            val toNodeId: String,
            val toPinId: String
        ) : Event
        data class RemoveConnection(val connectionId: String) : Event
        data class UpdateSelectedColor(val color: Triple<Float, Float, Float>) : Event
    }

    @Serializable
    data class SerializableNode(
        val id: String,
        val type: String,
        val x: Float = 0f,
        val y: Float = 0f,
        val properties: Map<String, String> = emptyMap()
    )

    @Serializable
    data class SerializableConnection(
        val id: String,
        val fromNodeId: String,
        val fromPinId: String,
        val toNodeId: String,
        val toPinId: String
    )

    @Serializable
    data class SerializableLED(
        val x: Int,
        val y: Int,
        val color: Triple<Float, Float, Float>
    )

    @Serializable
    data class CompositionChainDeviceState(
        val nodes: List<SerializableNode> = emptyList(),
        val connections: List<SerializableConnection> = emptyList(),
        val selectedColor: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
        val preRenderedFrames: Map<Int, List<SerializableLED>> = emptyMap()
    ) : DeviceState()
}
