package dev.anthonyhfm.amethyst.ui.contextmenu

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

@Composable
actual fun ContextMenuArea(
    items: List<ContextMenuItem>,
    content: @Composable (BoxScope.() -> Unit),
) {

}