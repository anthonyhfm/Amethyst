package dev.anthonyhfm.amethyst.gem.node

import dev.anthonyhfm.amethyst.gem.GemNodeDescriptor
import dev.anthonyhfm.amethyst.gem.GemNodeTypeId

/**
 * Registry that maps [GemNodeTypeId] values to their [GemNodeDefinition] instances.
 *
 * Use [GemNodeDefinitionRegistry.builtIns] for the default built-in set, or build a custom
 * registry with [of].
 */
class GemNodeDefinitionRegistry private constructor(
    private val byType: Map<GemNodeTypeId, GemNodeDefinition>,
    private val byTypeId: Map<String, List<GemNodeDefinition>>
) {
    fun all(): List<GemNodeDefinition> = byTypeId.values.flatten()

    fun descriptors(): List<GemNodeDescriptor> = all().map { it.descriptor }

    fun find(type: GemNodeTypeId): GemNodeDefinition? = byType[type]

    fun require(type: GemNodeTypeId): GemNodeDefinition = requireNotNull(find(type)) {
        "Unknown gem node definition for type: ${type.typeId}@${type.version}"
    }

    fun findLatest(typeId: String): GemNodeDefinition? = byTypeId[typeId]?.firstOrNull()

    companion object {
        val builtIns: GemNodeDefinitionRegistry by lazy {
            of(GemBuiltInNodeDefinitions.all)
        }

        fun of(definitions: Iterable<GemNodeDefinition>): GemNodeDefinitionRegistry {
            val list = definitions.toList()
            val duplicateTypes = list
                .groupBy { it.descriptor.type }
                .filterValues { it.size > 1 }
                .keys
            require(duplicateTypes.isEmpty()) {
                "Duplicate gem node definitions: ${duplicateTypes.sortedWith(compareBy<GemNodeTypeId>({ it.typeId }, { it.version.major }, { it.version.minor }, { it.version.hotfix })).joinToString { "${it.typeId}@${it.version}" }}"
            }

            val byType = list.associateBy { it.descriptor.type }
            val byTypeId = list
                .groupBy { it.descriptor.type.typeId }
                .mapValues { (_, versions) ->
                    versions.sortedWith(
                        compareByDescending<GemNodeDefinition> { it.descriptor.type.version.major }
                            .thenByDescending { it.descriptor.type.version.minor }
                            .thenByDescending { it.descriptor.type.version.hotfix }
                    )
                }

            return GemNodeDefinitionRegistry(byType = byType, byTypeId = byTypeId)
        }
    }
}
