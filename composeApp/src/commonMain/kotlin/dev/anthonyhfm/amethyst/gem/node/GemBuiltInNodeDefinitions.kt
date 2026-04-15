package dev.anthonyhfm.amethyst.gem.node

import dev.anthonyhfm.amethyst.gem.node.host.HostLedInputNode
import dev.anthonyhfm.amethyst.gem.node.host.HostLedOutputNode
import dev.anthonyhfm.amethyst.gem.node.led.LedPackNode
import dev.anthonyhfm.amethyst.gem.node.led.LedUnpackNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicAndNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicBranchNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicGateNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicNotNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicNumberCompareNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicOrNode
import dev.anthonyhfm.amethyst.gem.node.logic.LogicXorNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberAbsNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberAddNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberCeilNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberClampNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberDivideNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberFloorNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberMaxNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberMinNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberMultiplyNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberRoundNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberSqrtNode
import dev.anthonyhfm.amethyst.gem.node.math.NumberSubtractNode
import dev.anthonyhfm.amethyst.gem.node.structure.SubgraphCallNode
import dev.anthonyhfm.amethyst.gem.node.timing.DelayNode
import dev.anthonyhfm.amethyst.gem.node.value.ConstantBooleanNode
import dev.anthonyhfm.amethyst.gem.node.value.ConstantColorNode
import dev.anthonyhfm.amethyst.gem.node.value.ConstantNumberNode

/**
 * Canonical list of all built-in [GemNodeDefinition] instances.
 *
 * Referenced by [GemNodeDefinitionRegistry.builtIns] to construct the default execution registry.
 */
object GemBuiltInNodeDefinitions {
    val all: List<GemNodeDefinition> = listOf(
        // Host I/O
        HostLedInputNode,
        HostLedOutputNode,
        // Constants
        ConstantNumberNode,
        ConstantBooleanNode,
        ConstantColorNode,
        // LED
        LedUnpackNode,
        LedPackNode,
        // Math
        NumberAddNode,
        NumberSubtractNode,
        NumberMultiplyNode,
        NumberDivideNode,
        NumberClampNode,
        NumberSqrtNode,
        NumberAbsNode,
        NumberFloorNode,
        NumberCeilNode,
        NumberRoundNode,
        NumberMinNode,
        NumberMaxNode,
        // Logic
        LogicAndNode,
        LogicOrNode,
        LogicNotNode,
        LogicXorNode,
        LogicNumberCompareNode,
        LogicBranchNode,
        LogicGateNode,
        // Structure
        SubgraphCallNode,
        // Timing
        DelayNode,
    )
}
