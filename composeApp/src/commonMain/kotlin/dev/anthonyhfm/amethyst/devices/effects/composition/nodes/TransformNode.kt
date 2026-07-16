package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.DialType

import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.AutomatableDial
import dev.anthonyhfm.amethyst.devices.effects.composition.automation.automationParameters

abstract class TransformNode : CompositionNodeDefinition {
    override val hasInput = true
    override val hasOutput = true
    override val pickerCategory = CompositionNodePickerCategory.Transform
}

@Composable
internal fun NodeControls(content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        content()
    }

@Composable
internal fun <T> Dial(
    type: DialType<T>,
    value: T,
    defaultValue: T,
    title: String,
    text: String,
    onValueChange: (T) -> Unit,
) {
    val node = LocalCompositionNode.current
    val parameter = if (node != null) getParameterByTitle(title, node.automationParameters()) else null
    if (parameter != null) {
        AutomatableDial(
            parameterId = parameter.id,
            type = type,
            value = value,
            defaultValue = defaultValue,
            title = title,
            text = text,
            onValueChange = onValueChange,
        )
    } else {
        dev.anthonyhfm.amethyst.ui.components.primitives.Dial(
            type = type,
            value = value,
            defaultValue = defaultValue,
            title = title,
            text = text,
            onValueChange = onValueChange,
        )
    }
}
