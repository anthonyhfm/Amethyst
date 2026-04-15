package dev.anthonyhfm.amethyst.gem.node.value

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemNodeInstance
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import dev.anthonyhfm.amethyst.gem.node.GemNodeDefinition
import dev.anthonyhfm.amethyst.gem.node.GemNodeExecutionContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.ui.theme.small
import kotlinx.serialization.json.encodeToJsonElement

object ConstantNumberNode : GemNodeDefinition() {
    override val descriptor = GemBuiltInNodes.constantNumber

    @Composable
    override fun Content(
        node: GemNodeInstance,
        onNodeChange: (GemNodeInstance) -> Unit,
        modifier: Modifier
    ) {
        val currentValue = remember(node.serializedState) {
            val jsonValue = node.serializedState["value"] ?: return@remember 0.0
            GemJsonPersistence.json.decodeFromJsonElement(GemValue.serializer(), jsonValue)
                .let { (it as? GemValue.Number)?.value ?: 0.0 }
        }
        var text by remember(currentValue) { mutableStateOf(currentValue.toString()) }

        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        val parsed = text.toDoubleOrNull()
                        if (parsed != null) {
                            val newState = node.serializedState.toMutableMap().apply {
                                this["value"] = GemJsonPersistence.json.encodeToJsonElement(
                                    GemValue.serializer(),
                                    GemValue.Number(parsed)
                                )
                            }
                            onNodeChange(node.copy(serializedState = newState))
                        }
                    }
                },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            cursorBrush = SolidColor(Theme[colors][foreground]),
            textStyle = Theme[typography][small].copy(color = Theme[colors][foreground])
        )
    }

    override fun execute(context: GemNodeExecutionContext) {
        val value = context.nodePlan.state["value"]
        if (value == null) {
            context.error(
                code = GemRuntimeDiagnosticCode.MISSING_STATE_VALUE,
                message = "Constant node '${context.nodeId}' is missing runtime state 'value'.",
                pinId = "value"
            )
        } else {
            context.emitDatum("value", dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDatum.Value(value))
        }
    }
}
