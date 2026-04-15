package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

// --- Menubar (horizontal bar) ---

@Composable
fun Menubar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(DefaultShape)
            .background(Theme[colors][background])
            .border(1.dp, Theme[colors][border], DefaultShape)
            .padding(4.dp),
        content = content,
    )
}

// --- MenubarMenu ---

@Composable
fun MenubarMenu(
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

// --- MenubarTrigger ---

@Composable
fun MenubarTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = if (hovered) Theme[colors][accent] else Theme[colors][background]
    val fgColor = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]

    UnstyledButton(
        onClick = onClick,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
            .clip(SmallShape)
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
            }
        }
    }
}

// --- MenubarContent (Panel) ---

@Composable
fun MenubarContent(
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

// --- MenubarItem ---

@Composable
fun MenubarItem(
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

// --- MenubarSeparator ---

@Composable
fun MenubarSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Theme[colors][border])
    )
}

// --- MenubarLabel ---

@Composable
fun MenubarLabel(
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

// --- MenubarShortcut ---

@Composable
fun RowScope.MenubarShortcut(
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

// --- MenubarCheckboxItem ---

@Composable
fun MenubarCheckboxItem(
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

// --- MenubarRadioItem ---

@Composable
fun MenubarRadioItem(
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

// --- MenubarSub ---

@Composable
fun MenubarSub(
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

// --- MenubarSubTrigger ---

@Composable
fun MenubarSubTrigger(
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

// --- MenubarSubContent ---

@Composable
fun MenubarSubContent(
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
