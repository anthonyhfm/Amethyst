package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GemAsset(
    val schemaVersion: Version = Gem.phase1SchemaVersion,
    val metadata: GemAssetMetadata = GemAssetMetadata(),
    val definition: GemDefinition = GemDefinition(),
    val extensions: Map<String, JsonElement> = emptyMap()
) {
    fun graph(graphId: String = Gem.rootGraphId): GemGraph? = definition.graph(graphId)

    fun updateGraph(
        graphId: String = Gem.rootGraphId,
        transform: (GemGraph) -> GemGraph
    ): GemAsset {
        val updatedDefinition = definition.updateGraph(graphId, transform)
        return if (updatedDefinition == definition) this else copy(definition = updatedDefinition)
    }

    fun putNode(
        node: GemNodeInstance,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateGraph(graphId) { it.putNode(node) }

    fun removeNode(
        nodeId: String,
        graphId: String = Gem.rootGraphId
    ): GemAsset {
        val updatedAsset = updateGraph(graphId) { it.removeNode(nodeId) }
        if (updatedAsset == this) {
            return this
        }

        return updatedAsset.copy(
            definition = updatedAsset.definition.copy(
                exposedParameters = updatedAsset.definition.exposedParameters.map { parameter ->
                    parameter.withoutBindingsFor(
                        graphId = graphId,
                        nodeId = nodeId
                    )
                }
            )
        )
    }

    fun moveNode(
        nodeId: String,
        position: GemNodePosition,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateGraph(graphId) { it.moveNode(nodeId, position) }

    fun connect(
        connection: GemConnection,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateGraph(graphId) { it.connect(connection) }

    fun disconnect(
        connectionId: String,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateGraph(graphId) { it.disconnect(connectionId) }

    fun disconnect(
        pin: GemPinRef,
        graphId: String = Gem.rootGraphId
    ): GemAsset = updateGraph(graphId) { it.disconnect(pin) }
}

@Serializable
data class GemAssetMetadata(
    val id: String = "",
    val name: String = "Untitled Gem",
    val description: String = "",
    val category: GemCategory = GemCategory(),
    val assetVersion: Version = Version(1, 0, 0),
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false
)

@Serializable
data class GemCategory(
    val id: String = "general",
    val label: String = "General"
)

@Serializable
data class GemDefinition(
    val rootGraph: GemGraph = GemGraph(
        id = Gem.rootGraphId,
        kind = GemGraphKind.ROOT
    ),
    val subgraphs: List<GemGraph> = emptyList(),
    val host: GemHostIoContract = GemHostIoContract(),
    val exposedParameters: List<GemExposedParameter> = emptyList(),
    val defaultState: GemDefaultState = GemDefaultState()
) {
    fun graph(graphId: String = Gem.rootGraphId): GemGraph? = when (graphId) {
        rootGraph.id -> rootGraph
        else -> subgraphs.firstOrNull { it.id == graphId }
    }

    fun updateGraph(
        graphId: String = Gem.rootGraphId,
        transform: (GemGraph) -> GemGraph
    ): GemDefinition {
        if (rootGraph.id == graphId) {
            return copy(rootGraph = rootGraph.mutate(transform))
        }

        var updated = false
        val nextSubgraphs = subgraphs.map { graph ->
            if (graph.id != graphId) {
                graph
            } else {
                updated = true
                graph.mutate(transform)
            }
        }

        return if (updated) copy(subgraphs = nextSubgraphs) else this
    }
}

@Serializable
data class GemDefaultState(
    val exposedParameterValues: Map<String, GemValue> = emptyMap(),
    val hostDeviceDefaults: Map<String, JsonElement> = emptyMap()
)

private fun GemGraph.mutate(transform: (GemGraph) -> GemGraph): GemGraph {
    val updated = transform(this)
    require(updated.id == id) {
        "Gem graph updates must preserve graph identity for '$id'."
    }
    require(updated.kind == kind) {
        "Gem graph updates must preserve graph kind for '$id'."
    }
    return updated
}

private fun GemExposedParameter.withoutBindingsFor(
    graphId: String,
    nodeId: String
): GemExposedParameter = copy(
    bindings = bindings.filterNot { binding ->
        binding.graphId == graphId && binding.nodeId == nodeId
    }
)

/**
 * Creates a copy of this asset with a fresh ID and a name indicating it is a duplicate.
 * The [newId] defaults to a new workspace-scoped UUID to avoid conflicts.
 */
fun GemAsset.duplicate(newId: String = "gem://workspace/${UUID.randomUUID()}"): GemAsset =
    copy(
        metadata = metadata.copy(
            id = newId,
            name = "${metadata.name} Copy"
        )
    )
