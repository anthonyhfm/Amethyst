package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

actual fun Modifier.rightClickable(onRightClick: (position: Offset) -> Unit): Modifier = this