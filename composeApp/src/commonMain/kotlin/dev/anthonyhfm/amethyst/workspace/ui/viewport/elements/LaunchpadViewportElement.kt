package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import dev.anthonyhfm.amethyst.core.midi.data.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.selection.Selectable
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.LaunchpadPreviewState
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class LaunchpadViewportElement(
    override var position: MutableState<Offset> = mutableStateOf(Offset(0f, 0f)),
) : ViewportElement, Selectable {
    abstract val name: String
    abstract override var shape: Shape
    abstract override var size: Size
    abstract val layout: LaunchpadLayout

    override val selectionUUID: String = UUID.randomUUID()

    val renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var deviceConfig: ProjectDeviceConfig = ProjectDeviceConfig()
    val previewState: LaunchpadPreviewState = LaunchpadPreviewState()

    val screen = Screen()

    init {
        screen.screenExit = { u, c ->
            deviceConfig.launchpadDevice?.sendUpdate(u, c)

            renderScope.launch {
                previewState.sendToPreview(u)
            }
        }
    }

    var onEvent: ((WorkspaceContract.Event) -> Unit)? = null

    var indexInViewport: Int = 0

    override val actions: @Composable RowScope.() -> Unit
        get() = {
            FilledIconButton(
                onClick = {
                    onEvent?.invoke(WorkspaceContract.Event.OnClickDeviceConfigure(indexInViewport))
                }
            ) {
                Icon(Icons.Default.Settings, null)
            }
        }

    abstract override val content: @Composable (() -> Unit)
}