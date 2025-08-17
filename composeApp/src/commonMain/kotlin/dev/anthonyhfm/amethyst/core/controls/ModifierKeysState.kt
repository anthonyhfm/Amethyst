package dev.anthonyhfm.amethyst.core.controls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed

object ModifierKeysState {
    var isShiftPressed by mutableStateOf(false)
        private set

    var isCtrlPressed by mutableStateOf(false)
        private set

    var isAltPressed by mutableStateOf(false)
        private set

    fun updateFromKeyEvent(event: KeyEvent) {
        isShiftPressed = event.isShiftPressed
        isCtrlPressed = event.isCtrlPressed
        isAltPressed = event.isAltPressed
    }
}
