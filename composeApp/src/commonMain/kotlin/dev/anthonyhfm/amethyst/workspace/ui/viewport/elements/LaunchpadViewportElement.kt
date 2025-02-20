package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.launchpad.previews.LaunchpadPro
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportElement

data class LaunchpadViewportElement(
    override var position: MutableState<Offset> = mutableStateOf(Offset(0f, 0f)),
) : ViewportElement {
    override var shape: Shape = RoundedCornerShape(6)
    override val size = Size(480f, 480f)

    var onEvent: ((WorkspaceContract.Event) -> Unit)? = null
    var indexInViewport: Int = 0

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
                    onEvent?.invoke(WorkspaceContract.Event.OnClickDeviceConfigure(indexInViewport))
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