package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame

val LocalCompositionNode = staticCompositionLocalOf<CompositionNode?> { null }
val LocalAutomationHandler = staticCompositionLocalOf<((parameterId: String, automated: Boolean, remove: Boolean) -> Unit)?> { null }
val LocalNodeChangeCallbacks = staticCompositionLocalOf<NodeChangeCallbacks> { NodeChangeCallbacks() }

data class NodeChangeCallbacks(
    val onStart: () -> Unit = {},
    val onFinish: () -> Unit = {},
)

fun getParameterByTitle(title: String, parameters: List<CompositionAutomationParameter>): CompositionAutomationParameter? {
    val cleanTitle = title.trim().lowercase()
    return parameters.firstOrNull {
        val cleanLabel = it.label.trim().lowercase()
        val cleanId = it.id.trim().lowercase()
        cleanLabel == cleanTitle || cleanId == cleanTitle ||
        (cleanTitle == "opacity" && (cleanLabel == "alpha" || cleanId == "alpha"))
    }
}

interface CompositionNodeDefinition {
    val type: String
    val label: String
    val icon: ImageVector
    val hasInput: Boolean
    val hasOutput: Boolean
    val isOutput: Boolean get() = false
    val pickerCategory: CompositionNodePickerCategory? get() = null
    val automationParameters: List<CompositionAutomationParameter> get() = emptyList()

    /**
     * Source generators describe motion in their own raw 0..1 time domain. Keep only the
     * part of that domain which actually paints a workspace LED, then stretch it back over
     * the composition timeline. Time nodes are evaluated afterwards and can still add
     * intentional gaps.
     */
    val stretchVisibleSourceTimeline: Boolean get() = !hasInput && hasOutput

    val bodyWidth: Dp get() = 188.dp
    val bodyHeight: Dp get() = 96.dp

    fun defaultState(): CompositionNodeState
    fun sourceFrames(node: CompositionNode, context: EvaluationContext): List<GeometryFrame> = emptyList()
    fun transformFrames(
        node: CompositionNode,
        context: EvaluationContext,
        inputFrames: List<GeometryFrame>,
    ): List<GeometryFrame> = inputFrames

    fun inputContext(node: CompositionNode, context: EvaluationContext): EvaluationContext = context

    fun evaluate(
        graph: dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionGraph,
        node: CompositionNode,
        context: EvaluationContext,
    ): List<GeometryFrame>? = null
    
    @Composable
    fun NodeBody(
        node: CompositionNode,
        onNodeChange: (CompositionNode) -> Unit,
    )
}

enum class CompositionNodePickerCategory(val label: String) {
    Generators("Generators"),
    Transform("Transform"),
    Color("Color"),
    Time("Time"),
    Effects("Effects"),
}
