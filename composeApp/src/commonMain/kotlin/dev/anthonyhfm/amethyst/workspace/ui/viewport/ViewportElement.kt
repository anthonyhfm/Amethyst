package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.launchpad.previews.LaunchpadPro

sealed interface ViewportElement {
    var position: Offset
    val size: Size
    val shape: Shape
    val actions: @Composable RowScope.() -> Unit
    val content: @Composable () -> Unit

    data class ViewportLaunchpad(
        override var position: Offset = Offset(0f, 0f),
    ) : ViewportElement {
        override var shape: Shape = RoundedCornerShape(6)
        override val size = Size(480f, 480f)

        override val actions: @Composable RowScope.() -> Unit
            get() = {
                FilledIconButton(
                    onClick = {

                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, null)
                }

                FilledIconButton(
                    onClick = {

                    }
                ) {
                    Icon(Icons.Default.Settings, null)
                }

                FilledIconButton(
                    onClick = {

                    }
                ) {
                    Icon(Icons.Default.Delete, null)
                }
            }

        override val content: @Composable (() -> Unit)
            get() = {
                LaunchpadPro(
                    modifier = Modifier
                        .width(size.width.dp)
                        .height(size.height.dp)
                )
            }
    }
}