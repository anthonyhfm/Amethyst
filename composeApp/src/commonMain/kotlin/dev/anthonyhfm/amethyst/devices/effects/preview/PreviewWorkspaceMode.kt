package dev.anthonyhfm.amethyst.devices.effects.preview

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Custom workspace mode activated by the PreviewChainDevice when multiple Launchpad
 * devices are present.  Pad clicks on the large viewport devices are captured via
 * [onPadInteraction] and routed through the PreviewChainDevice's own [signalExit],
 * so signals enter the chain at the pre-effect insertion point.
 *
 * Heaven.devices get their [onCapturePad] set when the mode opens and cleared on close.
 * Cleanup happens regardless of how the mode exits (X button, keyboard, or explicit [close]).
 */
class PreviewWorkspaceMode(
    private val onPadInteraction: (down: Boolean, x: Int, y: Int) -> Unit,
) : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Preview"
    override val selectable: Boolean = false

    var modeClose: (() -> Unit)? = null

    /** Call after [WorkspaceRepository.switchMode] to wire up pad capture on all devices. */
    fun wake() {
        Heaven.devices.forEach { device ->
            device.onCapturePad = { (down, x, y) ->
                onPadInteraction(down, x, y)
            }
        }

        // Auto-release pad capture no matter how the mode exits (X button, keyboard, or close()).
        CoroutineScope(Dispatchers.Main).launch {
            WorkspaceRepository.mode.collect { currentMode ->
                if (currentMode !== this@PreviewWorkspaceMode) {
                    releasePadCapture()
                    cancel()
                }
            }
        }
    }

    /** Releases pad capture from all devices and fires [modeClose]. */
    fun close() {
        releasePadCapture()
        modeClose?.invoke()
    }

    private fun releasePadCapture() {
        Heaven.devices.forEach { device ->
            device.onCapturePad = null
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val isExitKey = event.key == Key.Escape ||
            ((event.isCtrlPressed || event.isMetaPressed) && event.key == Key.W)
        if (isExitKey) {
            close()
            return true
        }
        return false
    }
}
