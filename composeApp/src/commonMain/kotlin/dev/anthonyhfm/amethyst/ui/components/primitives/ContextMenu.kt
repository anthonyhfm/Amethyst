package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
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
import kotlinx.coroutines.delay

// --- Composition Locals ---

private val LocalContextMenuDismiss = staticCompositionLocalOf<() -> Unit> { {} }

// --- Position Providers ---

/** Places the popup at the cursor position within the anchor bounds. */
private class CursorPositionProvider(
    private val cursorOffset: Offset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + cursorOffset.x.toInt()
        val y = anchorBounds.top + cursorOffset.y.toInt()
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** Places a sub-menu to the right of the anchor (or left if insufficient space). */
private class SubMenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (anchorBounds.right + popupContentSize.width <= windowSize.width) {
            anchorBounds.right
        } else {
            anchorBounds.left - popupContentSize.width
        }
        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = anchorBounds.top.coerceIn(
                0,
                (windowSize.height - popupContentSize.height).coerceAtLeast(0),
            ),
        )
    }
}

// =============================================================================
// ContextMenu
// =============================================================================

/**
 * Right-click context menu. Wraps [trigger] content and shows a popup at the
 * cursor position on secondary click.
 */
@Composable
fun ContextMenu(
    modifier: Modifier = Modifier,
    onRightClick: ((Offset) -> Unit)? = null,
    trigger: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier.rightClickable { position ->
            cursorPosition = position
            onRightClick?.invoke(position)
            expanded = true
        },
    ) {
        trigger()

        if (expanded) {
            Popup(
                popupPositionProvider = CursorPositionProvider(cursorPosition),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                CompositionLocalProvider(LocalContextMenuDismiss provides { expanded = false }) {
                    ContextMenuContent(content = content)
                }
            }
        }
    }
}

// =============================================================================
// ContextMenuContent
// =============================================================================

/** Styled popup container for context-menu items. */
@Composable
fun ContextMenuContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = 128.dp)
            .shadow(8.dp, DefaultShape)
            .clip(DefaultShape)
            .background(Theme[colors][popover])
            .border(1.dp, Theme[colors][border], DefaultShape)
            .padding(4.dp),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][popoverForeground])) {
            content()
        }
    }
}

// =============================================================================
// ContextMenuItem
// =============================================================================

enum class ContextMenuItemVariant { Default, Destructive }

@Composable
fun ContextMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ContextMenuItemVariant = ContextMenuItemVariant.Default,
    inset: Boolean = false,
    dismissOnClick: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val dismiss = LocalContextMenuDismiss.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> Color.Transparent
        variant == ContextMenuItemVariant.Destructive && hovered ->
            Theme[colors][destructive].copy(alpha = 0.15f)
        hovered -> Theme[colors][accent]
        else -> Color.Transparent
    }

    val fgColor = when {
        !enabled -> Theme[colors][mutedForeground]
        variant == ContextMenuItemVariant.Destructive -> Theme[colors][destructive]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][popoverForeground]
    }

    UnstyledButton(
        onClick = {
            onClick()
            if (dismissOnClick) {
                dismiss()
            }
        },
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(
            start = if (inset) 32.dp else 8.dp,
            end = 8.dp,
            top = 6.dp,
            bottom = 6.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(bgColor)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

// =============================================================================
// ContextMenuCheckboxItem
// =============================================================================

@Composable
fun ContextMenuCheckboxItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = if (hovered && enabled) Theme[colors][accent] else Color.Transparent
    val fgColor = if (hovered && enabled) Theme[colors][accentForeground]
        else Theme[colors][popoverForeground]

    UnstyledButton(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(bgColor)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(14.dp),
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
                content()
            }
        }
    }
}

// =============================================================================
// ContextMenuRadioItem
// =============================================================================

@Composable
fun ContextMenuRadioItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dismissOnClick: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val dismiss = LocalContextMenuDismiss.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = if (hovered && enabled) Theme[colors][accent] else Color.Transparent
    val fgColor = if (hovered && enabled) Theme[colors][accentForeground]
        else Theme[colors][popoverForeground]

    UnstyledButton(
        onClick = {
            onClick()
            if (dismissOnClick) {
                dismiss()
            }
        },
        enabled = enabled,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(bgColor)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(14.dp),
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(FullShape)
                                .background(fgColor),
                        )
                    }
                }
                content()
            }
        }
    }
}

// =============================================================================
// ContextMenuLabel
// =============================================================================

@Composable
fun ContextMenuLabel(
    modifier: Modifier = Modifier,
    inset: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (inset) 32.dp else 8.dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(
                color = Theme[colors][popoverForeground],
                fontWeight = FontWeight.Medium,
            ),
        ) {
            content()
        }
    }
}

// =============================================================================
// ContextMenuSeparator
// =============================================================================

@Composable
fun ContextMenuSeparator(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Theme[colors][border]),
    )
}

// =============================================================================
// ContextMenuShortcut
// =============================================================================

/** Right-aligned keyboard shortcut hint inside a [ContextMenuItem] row. */
@Composable
fun RowScope.ContextMenuShortcut(
    text: String,
    modifier: Modifier = Modifier,
) {
    Spacer(Modifier.weight(1f))
    Text(
        text = text,
        style = Theme[typography][small].copy(
            color = Theme[colors][mutedForeground],
            fontSize = 12.sp,
        ),
    )
}

// =============================================================================
// ContextMenuSub  /  SubTrigger  /  SubContent
// =============================================================================

private class ContextMenuSubState {
    var expanded by mutableStateOf(false)
    var triggerHovered by mutableStateOf(false)
    var contentHovered by mutableStateOf(false)
}

private val LocalContextMenuSubState =
    staticCompositionLocalOf<ContextMenuSubState?> { null }

/** Wrapper for a sub-menu inside a context menu. */
@Composable
fun ContextMenuSub(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val subState = remember { ContextMenuSubState() }

    // Coordinate open/close with a short grace period so the cursor can
    // travel from the trigger to the sub-content popup.
    LaunchedEffect(subState.triggerHovered, subState.contentHovered) {
        if (subState.triggerHovered || subState.contentHovered) {
            subState.expanded = true
        } else {
            delay(150)
            if (!subState.triggerHovered && !subState.contentHovered) {
                subState.expanded = false
            }
        }
    }

    CompositionLocalProvider(LocalContextMenuSubState provides subState) {
        Box(modifier) {
            content()
        }
    }
}

/** Trigger item for a [ContextMenuSub]; displays a chevron on the right. */
@Composable
fun ContextMenuSubTrigger(
    modifier: Modifier = Modifier,
    inset: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val subState = LocalContextMenuSubState.current ?: return
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(hovered) { subState.triggerHovered = hovered }

    val highlighted = hovered || subState.expanded
    val bgColor = if (highlighted) Theme[colors][accent] else Color.Transparent
    val fgColor = if (highlighted) Theme[colors][accentForeground]
        else Theme[colors][popoverForeground]

    UnstyledButton(
        onClick = { subState.expanded = !subState.expanded },
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(
            start = if (inset) 32.dp else 8.dp,
            end = 8.dp,
            top = 6.dp,
            bottom = 6.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .background(bgColor),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fgColor,
                )
            }
        }
    }
}

/** Content panel for a [ContextMenuSub]; appears to the right of the trigger. */
@Composable
fun ContextMenuSubContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val subState = LocalContextMenuSubState.current ?: return

    if (subState.expanded) {
        val contentInteractionSource = remember { MutableInteractionSource() }
        val contentHovered by contentInteractionSource.collectIsHoveredAsState()

        LaunchedEffect(contentHovered) { subState.contentHovered = contentHovered }

        Popup(
            popupPositionProvider = SubMenuPositionProvider(),
            onDismissRequest = { subState.expanded = false },
        ) {
            Box(Modifier.hoverable(contentInteractionSource)) {
                ContextMenuContent(modifier = modifier, content = content)
            }
        }
    }
}
