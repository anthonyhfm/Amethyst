package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

expect fun Modifier.rightClickable(
    onRightClick: (position: Offset) -> Unit
): Modifier