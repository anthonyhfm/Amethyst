package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.typography

// --- NavigationMenu (root) ---

@Composable
fun NavigationMenu(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Theme[colors][background])
            .padding(4.dp),
        content = content,
    )
}

// --- NavigationMenuList ---

@Composable
fun NavigationMenuList(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
        content = content,
    )
}

// --- NavigationMenuItem ---

@Composable
fun NavigationMenuItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    UnstyledDropdownMenu(
        onExpandRequest = { expanded = true },
        modifier = modifier,
    ) {
        content()
    }
}

// --- NavigationMenuTrigger ---

@Composable
fun NavigationMenuTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = if (hovered) Theme[colors][accent] else Theme[colors][background]
    val fgColor = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]
    val rotation by animateFloatAsState(if (hovered) 180f else 0f)

    UnstyledButton(
        onClick = onClick,
        shape = DefaultShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
            .clip(DefaultShape)
            .background(bgColor),
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(
                fontWeight = FontWeight.Medium,
                color = fgColor,
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotation),
                    tint = fgColor,
                )
            }
        }
    }
}

// --- NavigationMenuContent ---

@Composable
fun NavigationMenuContent(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    UnstyledDropdownMenuPanel(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = DefaultShape,
        backgroundColor = Theme[colors][popover],
        contentColor = Theme[colors][popoverForeground],
        contentPadding = PaddingValues(12.dp),
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 200.dp)
            .shadow(8.dp, DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][popover], DefaultShape)
            .clip(DefaultShape),
        content = content,
    )
}

// --- NavigationMenuLink ---

@Composable
fun NavigationMenuLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        active -> Theme[colors][accent]
        hovered -> Theme[colors][accent]
        else -> Theme[colors][background]
    }
    val fgColor = when {
        active -> Theme[colors][accentForeground]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][foreground]
    }

    UnstyledButton(
        onClick = onClick,
        shape = DefaultShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
            .clip(DefaultShape)
            .background(bgColor),
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(
                fontWeight = FontWeight.Medium,
                color = fgColor,
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

// --- NavigationMenuIndicator ---

@Composable
fun NavigationMenuIndicator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Theme[colors][foreground])
    )
}
