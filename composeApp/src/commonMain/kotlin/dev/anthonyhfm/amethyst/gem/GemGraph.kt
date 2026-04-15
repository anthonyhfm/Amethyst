package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.Version
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GemGraph(
    val id: String,
    val kind: GemGraphKind,
    val label: String = id,
    val description: String = "",
    val nodes: List<GemNodeInstance> = emptyList(),
    val connections: List<GemConnection> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val subgraphInterface: GemSubgraphInterface = GemSubgraphInterface()
) {
    fun node(nodeId: String): GemNodeInstance? = nodes.firstOrNull { it.id == nodeId }

    fun connection(connectionId: String): GemConnection? = connections.firstOrNull { it.id == connectionId }

    fun putNode(node: GemNodeInstance): GemGraph = copy(
        nodes = nodes.replaceOrAppend(node, match = { it.id == node.id })
    )

    fun removeNode(nodeId: String): GemGraph = copy(
        nodes = nodes.filterNot { it.id == nodeId },
        connections = connections.filterNot { it.from.nodeId == nodeId || it.to.nodeId == nodeId }
    )

    fun updateNode(
        nodeId: String,
        transform: (GemNodeInstance) -> GemNodeInstance
    ): GemGraph {
        var updated = false
        val nextNodes = nodes.map { current ->
            if (current.id != nodeId) {
                current
            } else {
                updated = true
                transform(current).also { next ->
                    require(next.id == current.id) {
                        "Gem node updates must preserve node identity for '$nodeId'."
                    }
                }
            }
        }

        return if (updated) copy(nodes = nextNodes) else this
    }

    fun moveNode(
        nodeId: String,
        position: GemNodePosition
    ): GemGraph = updateNode(nodeId) { it.moveTo(position) }

    fun connect(connection: GemConnection): GemGraph = copy(
        connections = connections.filterNot { existing ->
            existing.id == connection.id || existing.to == connection.to
        } + connection
    )

    fun disconnect(connectionId: String): GemGraph = copy(
        connections = connections.filterNot { it.id == connectionId }
    )

    fun disconnect(pin: GemPinRef): GemGraph = copy(
        connections = connections.filterNot { it.from == pin || it.to == pin }
    )
}

@Serializable
enum class GemGraphKind {
    ROOT,
    SUBGRAPH
}

@Serializable
data class GemNodeInstance(
    val id: String,
    val type: GemNodeTypeId,
    val label: String = id,
    val pins: List<GemPin> = emptyList(),
    val serializedState: Map<String, JsonElement> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val subgraphId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val layout: GemNodeLayout = GemNodeLayout()
) {
    fun moveTo(position: GemNodePosition): GemNodeInstance = copy(
        layout = layout.copy(position = position)
    )
}

@Serializable
data class GemNodeLayout(
    val position: GemNodePosition = GemNodePosition()
)

@Serializable
data class GemNodePosition(
    val x: Float = 0f,
    val y: Float = 0f
)

@Serializable
data class GemNodeTypeId(
    val typeId: String,
    val version: Version = Version(1, 0, 0)
)

@Serializable
data class GemPin(
    val id: String,
    val label: String = id,
    val direction: GemPinDirection,
    val type: GemPinType,
    val required: Boolean = false,
    val defaultValue: GemValue? = null,
    val groupId: String? = null,
    val sortOrder: Int? = null
)

@Serializable
enum class GemPinDirection {
    INPUT,
    OUTPUT
}

@Serializable
enum class GemPinFamily {
    SIGNAL,
    VALUE
}

@Serializable
sealed interface GemPinType {
    val family: GemPinFamily

    @Serializable
    @SerialName("signal")
    data class Signal(
        val domain: GemSignalDomain
    ) : GemPinType {
        override val family: GemPinFamily = GemPinFamily.SIGNAL
    }

    @Serializable
    @SerialName("value")
    data class Value(
        val valueType: GemValueType
    ) : GemPinType {
        override val family: GemPinFamily = GemPinFamily.VALUE
    }

    /** Accepts or emits a signal of any domain. Compatible with any [Signal] type. */
    @Serializable
    @SerialName("any_signal")
    object AnySignal : GemPinType {
        override val family: GemPinFamily = GemPinFamily.SIGNAL
    }
}

@Serializable
enum class GemSignalDomain {
    LED,
    MIDI
}

@Serializable
data class GemConnection(
    val id: String,
    val from: GemPinRef,
    val to: GemPinRef
)

@Serializable
data class GemPinRef(
    val nodeId: String,
    val pinId: String
)

@Serializable
data class GemSubgraphInterface(
    val inputs: List<GemSubgraphInputPort> = emptyList(),
    val outputs: List<GemSubgraphOutputPort> = emptyList()
)

@Serializable
data class GemSubgraphInputPort(
    val id: String,
    val label: String = id,
    val type: GemPinType,
    val required: Boolean = false,
    val defaultValue: GemValue? = null,
    val bindings: List<GemPinRef> = emptyList(),
    val groupId: String? = null,
    val sortOrder: Int? = null
)

@Serializable
data class GemSubgraphOutputPort(
    val id: String,
    val label: String = id,
    val type: GemPinType,
    val binding: GemPinRef? = null,
    val groupId: String? = null,
    val sortOrder: Int? = null
)

@Serializable
data class GemHostIoContract(
    val assetShape: GemHostAssetShape = GemHostAssetShape.PROCESSOR,
    val supportedDomains: List<GemSignalDomain> = emptyList(),
    val inputs: List<GemHostPort> = emptyList(),
    val outputs: List<GemHostPort> = emptyList()
)

@Serializable
enum class GemHostAssetShape {
    SOURCE,
    PROCESSOR,
    SINK
}

@Serializable
data class GemHostPort(
    val id: String,
    val label: String = id,
    val domain: GemSignalDomain,
    val required: Boolean = false
)

@Serializable
data class GemExposedParameter(
    val id: String,
    val label: String = id,
    val description: String = "",
    val type: GemValueType,
    val defaultValue: GemValue,
    val groupId: String? = null,
    val sortOrder: Int? = null,
    val bindings: List<GemGraphBinding> = emptyList()
)

@Serializable
data class GemGraphBinding(
    val graphId: String = Gem.rootGraphId,
    val nodeId: String,
    val pinId: String,
    val targetKind: GemBindingTargetKind = GemBindingTargetKind.INPUT_PIN
)

@Serializable
enum class GemBindingTargetKind {
    INPUT_PIN,
    OUTPUT_PIN
}

private fun <T> List<T>.replaceOrAppend(
    item: T,
    match: (T) -> Boolean
): List<T> {
    val index = indexOfFirst(match)
    if (index < 0) {
        return this + item
    }

    return toMutableList().also { it[index] = item }
}
