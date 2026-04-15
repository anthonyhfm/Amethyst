package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

internal data class ToggleGroupContext(
    val variant: ToggleVariant,
    val size: ToggleSize,
    val disabled: Boolean,
    val isSelected: (String) -> Boolean,
    val onToggle: (String) -> Unit,
)

internal val LocalToggleGroupContext = staticCompositionLocalOf<ToggleGroupContext?> { null }

/**
 * A single-selection toggle group — only one item may be active at a time.
 */
@Composable
fun ToggleGroup(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: ToggleVariant = ToggleVariant.Default,
    size: ToggleSize = ToggleSize.Default,
    disabled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val context = remember(value, variant, size, disabled) {
        ToggleGroupContext(
            variant = variant,
            size = size,
            disabled = disabled,
            isSelected = { it == value },
            onToggle = onValueChange,
        )
    }

    CompositionLocalProvider(LocalToggleGroupContext provides context) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * A multiple-selection toggle group — zero or more items may be active.
 */
@Composable
fun ToggleGroup(
    value: Set<String>,
    onValueChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    variant: ToggleVariant = ToggleVariant.Default,
    size: ToggleSize = ToggleSize.Default,
    disabled: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val context = remember(value, variant, size, disabled) {
        ToggleGroupContext(
            variant = variant,
            size = size,
            disabled = disabled,
            isSelected = { it in value },
            onToggle = { key ->
                onValueChange(
                    if (key in value) value - key else value + key
                )
            },
        )
    }

    CompositionLocalProvider(LocalToggleGroupContext provides context) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * An individual item inside a [ToggleGroup].
 */
@Composable
fun ToggleGroupItem(
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val ctx = LocalToggleGroupContext.current
        ?: error("ToggleGroupItem must be used inside a ToggleGroup")

    val pressed = ctx.isSelected(value)
    val itemEnabled = enabled && !ctx.disabled
    val variant = ctx.variant
    val size = ctx.size

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
        onClick = { ctx.onToggle(value) },
        enabled = itemEnabled,
        shape = shape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = size.contentPadding,
        borderColor = borderColor,
        borderWidth = 1.dp,
        modifier = modifier
            .height(size.height)
            .alpha(if (itemEnabled) 1f else 0.5f)
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
