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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun Pagination(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
fun PaginationContent(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun PaginationItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun PaginationLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = DefaultShape

    val bg = when {
        isActive -> Theme[colors][accent]
        hovered -> Theme[colors][accent]
        else -> Color.Transparent
    }

    val fg = when {
        isActive -> Theme[colors][accentForeground]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][foreground]
    }

    val borderColor = if (isActive) Theme[colors][border] else Color.Unspecified

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        borderColor = borderColor,
        borderWidth = 1.dp,
        modifier = modifier
            .size(36.dp)
            .clip(shape)
            .background(bg),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fg)) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                content()
            }
        }
    }
}

@Composable
fun PaginationLink(
    page: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    PaginationLink(
        onClick = onClick,
        modifier = modifier,
        isActive = isActive,
    ) {
        Text(text = page.toString())
    }
}

@Composable
fun PaginationPrevious(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Previous",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = DefaultShape

    val bg = if (hovered) Theme[colors][accent] else Color.Transparent
    val fg = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bg),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = fg,
            )
            Text(
                text = text,
                style = Theme[typography][small],
                color = fg,
            )
        }
    }
}

@Composable
fun PaginationNext(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Next",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = DefaultShape

    val bg = if (hovered) Theme[colors][accent] else Color.Transparent
    val fg = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bg),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = Theme[typography][small],
                color = fg,
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = fg,
            )
        }
    }
}

@Composable
fun PaginationEllipsis(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.size(36.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "More pages",
            modifier = Modifier.size(16.dp),
            tint = Theme[colors][mutedForeground],
        )
    }
}
