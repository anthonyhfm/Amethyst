package dev.anthonyhfm.amethyst

import androidx.compose.ui.window.ComposeUIViewController
import dev.anthonyhfm.amethyst.ui.theme.ComposeAmethystTheme
import dev.anthonyhfm.amethyst.workspace.Workspace
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

fun MainViewController() = ComposeUIViewController {
    ComposeAmethystTheme {
        App()
    }
}

fun WorkspaceViewController(
    darkMode: Boolean,
    onBack: () -> Unit,
) = ComposeUIViewController {
    ComposeAmethystTheme(darkMode = darkMode) {
        Workspace(
            onBack = {
                WorkspaceRepository.clean()
                onBack()
            }
        )
    }
}
