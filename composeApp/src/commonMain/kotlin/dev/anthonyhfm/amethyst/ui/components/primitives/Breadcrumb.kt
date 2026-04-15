package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun Breadcrumb(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun BreadcrumbList(
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
fun BreadcrumbItem(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
fun BreadcrumbLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "",
                style = Theme[typography][small].copy(
                    color = if (hovered) Theme[colors][foreground] else Theme[colors][mutedForeground],
                ),
            )
            content()
        }
    }
}

@Composable
fun BreadcrumbLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = Theme[typography][small],
            color = if (hovered) Theme[colors][foreground] else Theme[colors][mutedForeground],
        )
    }
}

@Composable
fun BreadcrumbPage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][small],
        color = Theme[colors][foreground],
    )
}

@Composable
fun BreadcrumbSeparator(
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (content != null) {
            content()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Theme[colors][mutedForeground],
            )
        }
    }
}

@Composable
fun BreadcrumbEllipsis(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        UnstyledButton(
            onClick = onClick,
            indication = null,
            contentPadding = PaddingValues(0.dp),
            modifier = modifier,
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = "More",
                modifier = Modifier.size(16.dp),
                tint = Theme[colors][mutedForeground],
            )
        }
    } else {
        Icon(
            imageVector = Icons.Default.MoreHoriz,
            contentDescription = "More",
            modifier = modifier.size(16.dp),
            tint = Theme[colors][mutedForeground],
        )
    }
}
