package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.UnstyledCheckbox
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 16.dp,
    iconSize: Dp = 12.dp,
) {
    val bgColor by animateColorAsState(
        if (checked) Theme[colors][primary] else Color.Transparent
    )
    val borderColor by animateColorAsState(
        if (checked) Theme[colors][primary] else Theme[colors][mutedForeground]
    )

    UnstyledCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.5f),
        backgroundColor = bgColor,
        contentColor = Theme[colors][primaryForeground],
        enabled = enabled,
        shape = SmallShape,
        borderColor = borderColor,
        borderWidth = 1.dp,
        indication = null,
        checkIcon = {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
        }
    )
}
