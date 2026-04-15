package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

// --- Root ---

@Composable
fun DropdownMenu(
    expanded: Boolean,
    onExpandRequest: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    UnstyledDropdownMenu(
        onExpandRequest = onExpandRequest,
        modifier = modifier,
    ) {
        content()
    }
}

// --- Trigger ---

@Composable
fun DropdownMenuTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier,
        indication = null,
        content = content,
    )
}

// --- Content (Panel) ---

@Composable
fun DropdownMenuContent(
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
        contentPadding = PaddingValues(4.dp),
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(100)),
        modifier = modifier
            .width(IntrinsicSize.Max)
            .shadow(8.dp, DefaultShape)
            .background(Theme[colors][popover], DefaultShape)
            .clip(DefaultShape),
        content = content,
    )
}

// --- Item ---

@Composable
fun DropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val fgColor = when {
        !enabled -> Theme[colors][mutedForeground]
        destructive -> Theme[colors][dev.anthonyhfm.amethyst.ui.theme.destructive]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][popoverForeground]
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(
                if (hovered && enabled) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

// --- Separator ---

@Composable
fun DropdownMenuSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Theme[colors][border])
    )
}

// --- Label ---

@Composable
fun DropdownMenuLabel(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(
                fontWeight = FontWeight.SemiBold,
                color = Theme[colors][popoverForeground],
            )
        ) {
            content()
        }
    }
}

// --- Shortcut ---

@Composable
fun RowScope.DropdownMenuShortcut(
    text: String,
    modifier: Modifier = Modifier,
) {
    Spacer(Modifier.weight(1f))
    Text(
        text = text,
        style = Theme[typography][small].copy(
            fontSize = 12.sp,
            color = Theme[colors][mutedForeground],
        ),
        modifier = modifier,
    )
}

// --- Checkbox Item ---

@Composable
fun DropdownMenuCheckboxItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val fgColor = when {
        !enabled -> Theme[colors][mutedForeground]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][popoverForeground]
    }

    UnstyledButton(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(
                if (hovered && enabled) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(16.dp),
                ) {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = fgColor,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                content()
            }
        }
    }
}

// --- Radio Item ---

@Composable
fun DropdownMenuRadioItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val fgColor = when {
        !enabled -> Theme[colors][mutedForeground]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][popoverForeground]
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(
                if (hovered && enabled) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(16.dp),
                ) {
                    if (selected) {
                        // Filled circle indicator for radio selection
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(fgColor, SmallShape)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                content()
            }
        }
    }
}

// --- Group ---

@Composable
fun DropdownMenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, content = content)
}

// --- Sub Menu ---

@Composable
fun DropdownMenuSub(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var subExpanded by remember { mutableStateOf(false) }

    UnstyledDropdownMenu(
        onExpandRequest = { subExpanded = true },
        modifier = modifier,
    ) {
        content()
    }
}

// --- Sub Trigger ---

@Composable
fun DropdownMenuSubTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val fgColor = if (hovered) Theme[colors][accentForeground]
        else Theme[colors][popoverForeground]

    UnstyledButton(
        onClick = onClick,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(
                if (hovered) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fgColor,
                )
            }
        }
    }
}

// --- Sub Content ---

@Composable
fun DropdownMenuSubContent(
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
        contentPadding = PaddingValues(4.dp),
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(100)),
        modifier = modifier
            .width(IntrinsicSize.Max)
            .shadow(8.dp, DefaultShape)
            .background(Theme[colors][popover], DefaultShape)
            .clip(DefaultShape),
        content = content,
    )
}
