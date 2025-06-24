package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun Modifier.editorEventListener(
    onEvent: (EditorEvent) -> Unit
): Modifier

sealed interface EditorEvent {
    data object Save : EditorEvent
    data object Copy : EditorEvent
    data object Paste : EditorEvent
    data object Remove : EditorEvent
    data object Duplicate : EditorEvent
    data object SelectAll : EditorEvent
    data object Down : EditorEvent
    data object Up : EditorEvent
    data object Escape : EditorEvent
}