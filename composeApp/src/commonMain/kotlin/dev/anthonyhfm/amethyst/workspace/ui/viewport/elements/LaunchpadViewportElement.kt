package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import dev.anthonyhfm.amethyst.ui.previewdevices.PreviewState
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportElement

abstract class LaunchpadViewportElement(
    override var position: MutableState<Offset> = mutableStateOf(Offset(0f, 0f)),
) : ViewportElement {
    abstract override var shape: Shape
    abstract override var size: Size

    val previewState: PreviewState = PreviewState()

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

    abstract override val content: @Composable (() -> Unit)
}