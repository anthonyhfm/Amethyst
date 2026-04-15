package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuContent
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground

private class ChainContextMenuPositionProvider(
    private val offset: IntOffset,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left + offset.x).coerceIn(
            0,
            (windowSize.width - popupContentSize.width).coerceAtLeast(0),
        )
        val y = (anchorBounds.top + offset.y).coerceIn(
            0,
            (windowSize.height - popupContentSize.height).coerceAtLeast(0),
        )
        return IntOffset(x, y)
    }
}

@Composable
internal fun ChainContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val popupOffset = remember(offset, density) {
        with(density) {
            IntOffset(offset.x.roundToPx(), offset.y.roundToPx())
        }
    }

    Box {
        if (expanded) {
            Popup(
                popupPositionProvider = ChainContextMenuPositionProvider(popupOffset),
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = true),
            ) {
                ContextMenuContent(content = content)
            }
        }
    }
}

@Composable
internal fun NavigableChainContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.(
        onNavigate: (String) -> Unit,
        onBack: () -> Unit,
        currentLevel: String,
    ) -> Unit,
) {
    var navigationStack by remember(expanded) { mutableStateOf(listOf("main")) }

    ChainContextMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
    ) {
        AnimatedContent(
            targetState = navigationStack,
            transitionSpec = {
                val isGoingBack = targetState.size < initialState.size
                if (isGoingBack) {
                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(90)) +
                                scaleOut(targetScale = 0.92f, animationSpec = tween(90)),
                        )
                } else {
                    (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                        scaleIn(initialScale = 1.08f, animationSpec = tween(220, delayMillis = 90)))
                        .togetherWith(
                            fadeOut(animationSpec = tween(90)) +
                                scaleOut(targetScale = 1.08f, animationSpec = tween(90)),
                        )
                }.using(SizeTransform(clip = false))
            },
        ) { stack ->
            val level = stack.last()

            Column(modifier = Modifier.fillMaxWidth()) {
                if (level != "main") {
                    ChainContextMenuItem(
                        label = "Back",
                        icon = Lucide.ChevronLeft,
                        dismissOnClick = false,
                        onClick = {
                            if (navigationStack.size > 1) {
                                navigationStack = navigationStack.dropLast(1)
                            }
                        },
                    )
                    ContextMenuSeparator()
                }

                content(
                    { nextLevel -> navigationStack = navigationStack + nextLevel },
                    {
                        if (navigationStack.size > 1) {
                            navigationStack = navigationStack.dropLast(1)
                        }
                    },
                    level,
                )
            }
        }
    }
}

@Composable
internal fun ChainContextMenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    variant: ContextMenuItemVariant = ContextMenuItemVariant.Default,
    dismissOnClick: Boolean = false,
) {
    val contentColor = when {
        !enabled -> Theme[colors][mutedForeground]
        variant == ContextMenuItemVariant.Destructive -> Theme[colors][destructive]
        else -> Theme[colors][popoverForeground]
    }

    ContextMenuItem(
        onClick = onClick,
        enabled = enabled,
        variant = variant,
        dismissOnClick = dismissOnClick,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = contentColor,
        )
    }
}

@Composable
internal fun ChainContextMenuSubmenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val contentColor = if (enabled) {
        Theme[colors][popoverForeground]
    } else {
        Theme[colors][mutedForeground]
    }

    ContextMenuItem(
        onClick = onClick,
        enabled = enabled,
        dismissOnClick = false,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
        } else {
            Spacer(modifier = Modifier.size(16.dp))
        }

        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = contentColor,
        )

        Icon(
            imageVector = Lucide.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = contentColor,
        )
    }
}
