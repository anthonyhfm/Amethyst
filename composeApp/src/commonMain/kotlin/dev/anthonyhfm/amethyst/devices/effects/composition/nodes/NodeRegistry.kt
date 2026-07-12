package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode

object NodeRegistry {
    private val definitionsByType = mutableMapOf<String, CompositionNodeDefinition>()
    val definitions: List<CompositionNodeDefinition> get() = definitionsByType.values.toList()

    init {
        register(ScannerNode)
        register(NoiseNode)
        register(WaterdropNode)
        register(SpiralNode)
        register(RotateNode)
        register(MirrorNode)
        register(SymmetryNode)
        register(PinchNode)
        register(MoveNode)
        register(LoopNode)
        register(ReverseNode)
        register(OutputNode)
    }

    fun register(definition: CompositionNodeDefinition) {
        definitionsByType[definition.type] = definition
    }

    fun definitionFor(type: String): CompositionNodeDefinition? = definitionsByType[type]

    fun definitionFor(node: CompositionNode): CompositionNodeDefinition? =
        definitionFor(node.type)?.takeIf { it.acceptsState(node.state) }

    fun labelFor(type: String): String = definitionFor(type)?.label ?: type

    fun defaultStateFor(type: String): CompositionNodeState =
        definitionFor(type)?.defaultState() ?: OutputNodeState

    fun pickerDefinitions(category: CompositionNodePickerCategory): List<CompositionNodeDefinition> =
        definitions.filter { it.pickerCategory == category }
}
