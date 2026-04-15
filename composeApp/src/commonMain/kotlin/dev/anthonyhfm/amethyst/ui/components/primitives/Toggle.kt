package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

enum class ToggleVariant {
    Default,
    Outline,
}

enum class ToggleSize(
    val height: Dp,
    val contentPadding: PaddingValues,
    val gap: Dp,
) {
    Default(
        height = 40.dp,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        gap = 8.dp,
    ),
    Small(
        height = 36.dp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        gap = 6.dp,
    ),
    Large(
        height = 44.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        gap = 8.dp,
    ),
}

@Composable
fun Toggle(
    pressed: Boolean,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    variant: ToggleVariant = ToggleVariant.Default,
    size: ToggleSize = ToggleSize.Default,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = DefaultShape

    val bg: Color
    val bgHover: Color
    val fg: Color
    val borderColor: Color

    when (variant) {
        ToggleVariant.Default -> {
            bg = if (pressed) Theme[colors][accent] else Color.Transparent
            bgHover = if (pressed) Theme[colors][accent] else Theme[colors][muted]
            fg = if (pressed) Theme[colors][accentForeground] else Theme[colors][mutedForeground]
            borderColor = Color.Unspecified
        }
        ToggleVariant.Outline -> {
            bg = if (pressed) Theme[colors][accent] else Color.Transparent
            bgHover = if (pressed) Theme[colors][accent] else Theme[colors][muted]
            fg = if (pressed) Theme[colors][accentForeground] else Theme[colors][mutedForeground]
            borderColor = Theme[colors][input]
        }
    }

    val textStyle = Theme[typography][small].copy(color = fg)

    UnstyledButton(
        onClick = { onPressedChange(!pressed) },
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = size.contentPadding,
        borderColor = borderColor,
        borderWidth = 1.dp,
        modifier = modifier
            .height(size.height)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (hovered) bgHover else bg),
        content = {
            ProvideTextStyle(textStyle) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(size.gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    content()
                }
            }
        }
    )
}
