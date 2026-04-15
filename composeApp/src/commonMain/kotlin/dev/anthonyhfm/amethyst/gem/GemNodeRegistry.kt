package dev.anthonyhfm.amethyst.gem

class GemNodeRegistry private constructor(
    private val descriptorsByType: Map<GemNodeTypeId, GemNodeDescriptor>,
    private val descriptorsByTypeId: Map<String, List<GemNodeDescriptor>>
) {
    fun all(): List<GemNodeDescriptor> = descriptorsByTypeId.values.flatten()

    fun find(type: GemNodeTypeId): GemNodeDescriptor? = descriptorsByType[type]

    fun require(type: GemNodeTypeId): GemNodeDescriptor = requireNotNull(find(type)) {
        "Unknown gem node type: ${type.typeId}@${type.version}"
    }

    fun findAll(typeId: String): List<GemNodeDescriptor> = descriptorsByTypeId[typeId].orEmpty()

    fun findLatest(typeId: String): GemNodeDescriptor? = descriptorsByTypeId[typeId]?.firstOrNull()

    companion object {
        val builtIns: GemNodeRegistry by lazy {
            of(GemBuiltInNodes.all)
        }

        fun of(descriptors: Iterable<GemNodeDescriptor>): GemNodeRegistry {
            val descriptorList = descriptors.toList()
            val duplicateTypes = descriptorList
                .groupBy { it.type }
                .filterValues { it.size > 1 }
                .keys
            require(duplicateTypes.isEmpty()) {
                "Duplicate gem node descriptors: ${duplicateTypes.sortedWith(compareBy<GemNodeTypeId>({ it.typeId }, { it.version.major }, { it.version.minor }, { it.version.hotfix })).joinToString { "${it.typeId}@${it.version}" }}"
            }

            val byType = descriptorList.associateBy { it.type }
            val byTypeId = descriptorList
                .groupBy { it.type.typeId }
                .mapValues { (_, versions) ->
                    versions.sortedWith(
                        compareByDescending<GemNodeDescriptor> { it.type.version.major }
                            .thenByDescending { it.type.version.minor }
                            .thenByDescending { it.type.version.hotfix }
                    )
                }

            return GemNodeRegistry(
                descriptorsByType = byType,
                descriptorsByTypeId = byTypeId
            )
        }
    }
}
