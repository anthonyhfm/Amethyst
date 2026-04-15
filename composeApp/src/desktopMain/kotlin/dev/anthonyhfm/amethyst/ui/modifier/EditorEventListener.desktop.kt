package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

@Composable
actual fun Modifier.editorEventListener(onEvent: (EditorEvent) -> Unit): Modifier {
    // Meta-key: CTRL on Windows/Linux, CMD on macOS
    var meta by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }



    return this.onKeyEvent {
        meta = it.isCtrlPressed
        shift = it.isShiftPressed

        if (it.type == KeyEventType.KeyDown) {
            when (it.key) {
                Key.DirectionUp -> {
                    onEvent(EditorEvent.Up)
                    return@onKeyEvent true
                }

                Key.DirectionDown -> {
                    onEvent(EditorEvent.Down)
                    return@onKeyEvent true
                }

                Key.Escape -> {
                    onEvent(EditorEvent.Escape)
                    return@onKeyEvent true
                }
            }
        }

        return@onKeyEvent false
    }
}