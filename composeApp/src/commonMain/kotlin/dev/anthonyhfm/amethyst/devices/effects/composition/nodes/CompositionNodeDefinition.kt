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
}
