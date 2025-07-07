package dev.anthonyhfm.amethyst.workspace.modes.chain

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

object ChainModeKeyHandler {
    fun handleKeyInput(keyEvent: KeyEvent): Boolean {

        if (keyEvent.type == KeyEventType.KeyUp) {
            when (keyEvent.key) {
                Key.Backspace, Key.Delete -> {
                    val selection = WorkspaceRepository.selectionUUID.value
                    val mode = WorkspaceRepository.mode.value

                    selection?.let { selection ->
                        if (mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                            WorkspaceRepository.lightsChain.heavenChain.remove(selection)
                        } else if (mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                            WorkspaceRepository.samplingChain.heavenChain.remove(selection)
                        }

                        return true
                    }
                }
            }
        }

        return false
    }
}