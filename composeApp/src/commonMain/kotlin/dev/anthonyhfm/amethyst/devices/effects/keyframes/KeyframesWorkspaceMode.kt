package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

class KeyframesWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Keyframes Editor"
    override val selectable: Boolean = false

    var onVirtualDevicePress: ((x: Int, y: Int, offset: Offset) -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    fun virtualDevicePress(x: Int, y: Int, offset: Offset) {
        onVirtualDevicePress?.invoke(x, y, offset)
    }

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }

    @Composable
    fun EditorUI() {

    }
}