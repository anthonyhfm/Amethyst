package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun BlockEditorShortcuts(active: Boolean) {
    if (active) {
        DisposableEffect(Unit) {
            val release = WorkspaceRepository.blockEditorShortcuts()
            onDispose { release() }
        }
    }
}
