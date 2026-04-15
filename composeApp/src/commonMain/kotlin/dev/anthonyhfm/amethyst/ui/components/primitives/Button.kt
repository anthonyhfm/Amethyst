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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.destructiveForeground
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

enum class ButtonVariant {
    Default,
    Secondary,
    Destructive,
    Outline,
    Ghost,
    Link,
}

enum class ButtonSize(
    val height: Dp,
    val contentPadding: PaddingValues,
    val gap: Dp,
) {
    Default(
        height = 36.dp,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        gap = 8.dp,
    ),
    Small(
        height = 32.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        gap = 6.dp,
    ),
    Large(
        height = 40.dp,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        gap = 8.dp,
    ),
    Icon(
        height = 36.dp,
        contentPadding = PaddingValues(0.dp),
        gap = 0.dp,
    ),
    IconLarge(
        height = 72.dp,
        contentPadding = PaddingValues(0.dp),
        gap = 0.dp,
    ),
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Default,
    size: ButtonSize = ButtonSize.Default,
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
        ButtonVariant.Default -> {
            bg = Theme[colors][primary]
            bgHover = Theme[colors][primary].copy(alpha = 0.9f)
            fg = Theme[colors][primaryForeground]
            borderColor = Color.Unspecified
        }
        ButtonVariant.Secondary -> {
            bg = Theme[colors][secondary]
            bgHover = Theme[colors][secondary].copy(alpha = 0.8f)
            fg = Theme[colors][secondaryForeground]
            borderColor = Color.Unspecified
        }
        ButtonVariant.Destructive -> {
            bg = Theme[colors][destructive]
            bgHover = Theme[colors][destructive].copy(alpha = 0.9f)
            fg = Theme[colors][destructiveForeground]
            borderColor = Color.Unspecified
        }
        ButtonVariant.Outline -> {
            bg = Theme[colors][background]
            bgHover = Theme[colors][accent]
            fg = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]
            borderColor = Theme[colors][input]
        }
        ButtonVariant.Ghost -> {
            bg = Color.Transparent
            bgHover = Theme[colors][accent]
            fg = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]
            borderColor = Color.Unspecified
        }
        ButtonVariant.Link -> {
            bg = Color.Transparent
            bgHover = Color.Transparent
            fg = Theme[colors][primary]
            borderColor = Color.Unspecified
        }
    }

    val baseTextStyle: TextStyle = when (size) {
        ButtonSize.Small -> Theme[typography][small]
        else -> Theme[typography][p]
    }

    val textStyle = if (variant == ButtonVariant.Link && hovered) {
        baseTextStyle.copy(color = fg, textDecoration = TextDecoration.Underline)
    } else {
        baseTextStyle.copy(color = fg)
    }

    val sizeModifier = when (size) {
        ButtonSize.Icon, ButtonSize.IconLarge -> Modifier.size(size.height)
        else -> Modifier.height(size.height)
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = size.contentPadding,
        borderColor = borderColor,
        borderWidth = 1.dp,
        modifier = modifier
            .then(sizeModifier)
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
