package dev.anthonyhfm.amethyst

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import dev.anthonyhfm.amethyst.home.Home
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.ui.theme.MATERIAL_AMETHYST_THEME
import dev.anthonyhfm.amethyst.workspace.Workspace
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    var inWorkspace by remember { mutableStateOf(false) }

    AmethystTheme {
        if (inWorkspace) {
            Workspace(
                onBack = {
                    WorkspaceRepository.clean()
                    inWorkspace = false
                }
            )
        } else {
            Home(
                onOpenWorkspace = {
                    inWorkspace = true
                }
            )
        }
    }
}