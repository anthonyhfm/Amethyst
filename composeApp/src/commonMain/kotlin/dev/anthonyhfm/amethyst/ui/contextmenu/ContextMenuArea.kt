package dev.anthonyhfm.amethyst.ui.contextmenu

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ContextMenuArea(
    items: List<ContextMenuItem>,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
    ) {
        content()
    }
}