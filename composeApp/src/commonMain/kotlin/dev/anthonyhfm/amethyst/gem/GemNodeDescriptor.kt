package dev.anthonyhfm.amethyst.gem

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GemNodeDescriptor(
    val type: GemNodeTypeId,
    val metadata: GemNodeMetadata,
    val inputs: List<GemNodePinDescriptor> = emptyList(),
    val outputs: List<GemNodePinDescriptor> = emptyList(),
    val state: List<GemNodeStateField> = emptyList(),
    // Not serialized — carries pre-populated state for nodes that need it (e.g. host port ID).
    @kotlinx.serialization.Transient val defaultState: Map<String, JsonElement> = emptyMap()
) {
    init {
        val duplicatePinIds = (inputs + outputs)
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        require(duplicatePinIds.isEmpty()) {
            "Duplicate node pin IDs for ${type.typeId}@${type.version}: ${duplicatePinIds.sorted().joinToString()}"
        }

        val duplicateStateIds = state
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateStateIds.isEmpty()) {
            "Duplicate node state field IDs for ${type.typeId}@${type.version}: ${duplicateStateIds.sorted().joinToString()}"
        }
    }

    val pins: List<GemPin>
        get() = inputs.map { it.toPin(GemPinDirection.INPUT) } + outputs.map { it.toPin(GemPinDirection.OUTPUT) }

    fun pin(id: String): GemNodePinDescriptor? = (inputs + outputs).firstOrNull { it.id == id }

    fun stateField(id: String): GemNodeStateField? = state.firstOrNull { it.id == id }

    fun instantiate(
        nodeId: String,
        label: String = metadata.label,
        serializedState: Map<String, JsonElement> = defaultState
    ): GemNodeInstance = GemNodeInstance(
        id = nodeId,
        type = type,
        label = label,
        pins = pins,
        serializedState = serializedState
    )
}

@Serializable
data class GemNodeMetadata(
    val label: String,
    val category: GemNodeCategory = GemNodeCategory(),
    val description: String = ""
)

@Serializable
data class GemNodeCategory(
    val id: String = "general",
    val label: String = "General"
)

@Serializable
data class GemNodePinDescriptor(
    val id: String,
    val label: String = id,
    val type: GemPinType,
    val required: Boolean = false,
    val defaultValue: GemValue? = null,
    val groupId: String? = null,
    val sortOrder: Int? = null
) {
    fun toPin(direction: GemPinDirection): GemPin = GemPin(
        id = id,
        label = label,
        direction = direction,
        type = type,
        required = required,
        defaultValue = defaultValue,
        groupId = groupId,
        sortOrder = sortOrder
    )
}

@Serializable
data class GemNodeStateField(
    val id: String,
    val label: String = id,
    val type: GemValueType,
    val description: String = "",
    val required: Boolean = false,
    val defaultValue: GemValue? = null,
    val groupId: String? = null,
    val sortOrder: Int? = null
)

fun gemNodeDescriptor(
    typeId: String,
    version: dev.anthonyhfm.amethyst.core.util.Version = Gem.phase1SchemaVersion,
    label: String,
    category: GemNodeCategory = GemNodeCategory(),
    description: String = "",
    inputs: List<GemNodePinDescriptor> = emptyList(),
    outputs: List<GemNodePinDescriptor> = emptyList(),
    state: List<GemNodeStateField> = emptyList()
): GemNodeDescriptor = GemNodeDescriptor(
    type = GemNodeTypeId(typeId = typeId, version = version),
    metadata = GemNodeMetadata(
        label = label,
        category = category,
        description = description
    ),
    inputs = inputs,
    outputs = outputs,
    state = state
)

fun inputPin(
    id: String,
    type: GemPinType,
    label: String = id,
    required: Boolean = false,
    defaultValue: GemValue? = null,
    groupId: String? = null,
    sortOrder: Int? = null
): GemNodePinDescriptor = GemNodePinDescriptor(
    id = id,
    label = label,
    type = type,
    required = required,
    defaultValue = defaultValue,
    groupId = groupId,
    sortOrder = sortOrder
)

fun outputPin(
    id: String,
    type: GemPinType,
    label: String = id,
    groupId: String? = null,
    sortOrder: Int? = null
): GemNodePinDescriptor = GemNodePinDescriptor(
    id = id,
    label = label,
    type = type,
    groupId = groupId,
    sortOrder = sortOrder
)

fun stateField(
    id: String,
    type: GemValueType,
    label: String = id,
    description: String = "",
    required: Boolean = false,
    defaultValue: GemValue? = null,
    groupId: String? = null,
    sortOrder: Int? = null
): GemNodeStateField = GemNodeStateField(
    id = id,
    label = label,
    type = type,
    description = description,
    required = required,
    defaultValue = defaultValue,
    groupId = groupId,
    sortOrder = sortOrder
)
