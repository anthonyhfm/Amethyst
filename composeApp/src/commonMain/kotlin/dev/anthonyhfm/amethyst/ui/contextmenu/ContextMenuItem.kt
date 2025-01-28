package dev.anthonyhfm.amethyst.ui.contextmenu

import androidx.compose.ui.graphics.vector.ImageVector

data class ContextMenuItem(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit,
    val children: List<ContextMenuItem> = emptyList()
)