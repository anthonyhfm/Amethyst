package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.LocalContentColor
import com.composeunstyled.RadioButton
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun RadioGroup(
    value: String?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    com.composeunstyled.RadioGroup(
        value = value,
        onValueChange = onValueChange,
        contentDescription = contentDescription,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun RadioGroupItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RadioButton(
        value = value,
        modifier = modifier,
        enabled = enabled,
        indication = null,
        selectedColor = Theme[colors][primary],
        contentColor = Theme[colors][input],
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            val isSelected = LocalContentColor.current == Theme[colors][primary]
            RadioIndicator(selected = isSelected)
            Text(
                text = label,
                style = Theme[typography][small],
                color = Theme[colors][foreground],
            )
        }
    )
}

@Composable
private fun RadioIndicator(selected: Boolean) {
    val borderColor = if (selected) Theme[colors][primary] else Theme[colors][input]

    Box(
        modifier = Modifier
            .size(16.dp)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Theme[colors][primary], CircleShape)
            )
        }
    }
}
