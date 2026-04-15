package dev.anthonyhfm.amethyst.timeline.ui

import androidx.compose.runtime.Composable
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuShortcut

@Composable
fun TimelineContextMenuAction(
    label: String,
    onClick: () -> Unit,
    shortcut: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    ContextMenuItem(
        onClick = onClick,
        enabled = enabled,
        variant = if (destructive) ContextMenuItemVariant.Destructive else ContextMenuItemVariant.Default
    ) {
        Text(text = label)
        shortcut?.let { ContextMenuShortcut(text = it) }
    }
}
