package dev.anthonyhfm.amethyst.ui.contextmenu

import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    content: @Composable BoxScope.() -> Unit
) {
    val state = remember { ContextMenuState() }

    androidx.compose.foundation.ContextMenuArea(
        state = state,
        items = {
            items.map {
                androidx.compose.foundation.ContextMenuItem(
                    label = it.text,
                    onClick = it.onClick
                )
            }
        }
    ) {
        Box { content() }
    }
}