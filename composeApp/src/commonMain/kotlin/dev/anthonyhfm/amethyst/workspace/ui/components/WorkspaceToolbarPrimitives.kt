package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tooltip
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructiveForeground
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground

@Composable
fun WorkspaceToolbarSlideFromTopControls(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val slideBufferPx = with(LocalDensity.current) { 16.dp.roundToPx() }
    val hiddenOffsetY: (Int) -> Int = { fullHeight -> -(fullHeight + slideBufferPx) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = hiddenOffsetY),
        exit = slideOutVertically(targetOffsetY = hiddenOffsetY),
    ) {
        Row(
            modifier = Modifier.graphicsLayer { clip = false },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun WorkspaceToolbarSurface(
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(2.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(Theme[colors][muted], DefaultShape)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun workspaceToolbarContentColor(variant: ButtonVariant): Color {
    return when (variant) {
        ButtonVariant.Default -> Theme[colors][primaryForeground]
        ButtonVariant.Secondary -> Theme[colors][secondaryForeground]
        ButtonVariant.Destructive -> Theme[colors][destructiveForeground]
        ButtonVariant.Outline -> Theme[colors][foreground]
        ButtonVariant.Ghost -> Theme[colors][foreground]
        ButtonVariant.Link -> Theme[colors][primary]
    }
}

@Composable
private fun workspaceToolbarIconContentColor(
    variant: ButtonVariant,
    hovered: Boolean,
): Color {
    return when (variant) {
        ButtonVariant.Outline, ButtonVariant.Ghost -> {
            if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]
        }
        else -> workspaceToolbarContentColor(variant)
    }
}

@Composable
fun WorkspaceToolbarIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Ghost,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor: Color
    val hoverBackgroundColor: Color
    val borderColor: Color
    val hoverBorderColor: Color

    when (variant) {
        ButtonVariant.Default -> {
            backgroundColor = Theme[colors][primary]
            hoverBackgroundColor = Theme[colors][primary].copy(alpha = 0.9f)
            borderColor = Color.Transparent
            hoverBorderColor = Color.Transparent
        }
        ButtonVariant.Secondary -> {
            backgroundColor = Theme[colors][secondary]
            hoverBackgroundColor = Theme[colors][secondary].copy(alpha = 0.9f)
            borderColor = Color.Transparent
            hoverBorderColor = Color.Transparent
        }
        ButtonVariant.Destructive -> {
            backgroundColor = Theme[colors][dev.anthonyhfm.amethyst.ui.theme.destructive]
            hoverBackgroundColor = Theme[colors][dev.anthonyhfm.amethyst.ui.theme.destructive].copy(alpha = 0.9f)
            borderColor = Color.Transparent
            hoverBorderColor = Color.Transparent
        }
        ButtonVariant.Outline -> {
            backgroundColor = Theme[colors][background]
            hoverBackgroundColor = Theme[colors][accent]
            borderColor = Theme[colors][input]
            hoverBorderColor = Theme[colors][accent]
        }
        ButtonVariant.Ghost -> {
            backgroundColor = Color.Transparent
            hoverBackgroundColor = Theme[colors][accent]
            borderColor = Color.Transparent
            hoverBorderColor = Theme[colors][accent]
        }
        ButtonVariant.Link -> {
            backgroundColor = Color.Transparent
            hoverBackgroundColor = Color.Transparent
            borderColor = Color.Transparent
            hoverBorderColor = Color.Transparent
        }
    }

    Tooltip(
        text = contentDescription.orEmpty(),
        enabled = !contentDescription.isNullOrBlank(),
        anchor = {
            UnstyledButton(
                onClick = onClick,
                enabled = enabled,
                shape = SmallShape,
                interactionSource = interactionSource,
                indication = null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                borderColor = if (hovered) hoverBorderColor else borderColor,
                borderWidth = 1.dp,
                modifier = modifier
                    .height(32.dp)
                    .alpha(if (enabled) 1f else 0.5f)
                    .clip(SmallShape)
                    .background(if (hovered) hoverBackgroundColor else backgroundColor),
            ) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = contentDescription,
                    tint = workspaceToolbarIconContentColor(variant, hovered),
                    modifier = Modifier.size(16.dp),
                )
            }
        },
    )
}
