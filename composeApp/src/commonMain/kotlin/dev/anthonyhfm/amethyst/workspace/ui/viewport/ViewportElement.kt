package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape

interface ViewportElement {
    var position: MutableState<Offset>
    val size: Size
    val shape: Shape
    val actions: @Composable RowScope.() -> Unit
    val content: @Composable () -> Unit
}
