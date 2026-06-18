package dev.anthonyhfm.amethyst.devices.effects.composition

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.CompositionChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionDeviceContract.Event
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.CompositionWorkspaceView
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.StateFlow

class CompositionWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Composition"
    override val selectable: Boolean = false
    override val claimInputs: Boolean = true

    lateinit var state: StateFlow<CompositionChainDeviceState>
    var parentDevice: CompositionChainDevice? = null
    var onEvent: ((Event) -> Unit)? = null
    var modeWakeup: (() -> Unit)? = null
    var modeClose: (() -> Unit)? = null

    fun wake() {
        modeWakeup?.invoke()
    }

    fun close() {
        modeClose?.invoke()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isExitKey = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)
        if (isExitKey) {
            requestClose()
            return true
        }
        return false
    }

    private fun requestClose() {
        modeClose?.invoke()
        WorkspaceRepository.switchToPreviousMode()
    }

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        CompositionWorkspaceView(
            stateFlow = state,
            onEvent = { onEvent?.invoke(it) },
            paddingValues = paddingValues
        )
    }
}
