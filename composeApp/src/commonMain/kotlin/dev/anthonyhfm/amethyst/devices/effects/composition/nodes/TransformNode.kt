package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.DialType

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
) = dev.anthonyhfm.amethyst.ui.components.primitives.Dial(
    type = type,
    value = value,
    defaultValue = defaultValue,
    title = title,
    text = text,
    onValueChange = onValueChange,
)
