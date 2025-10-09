package dev.anthonyhfm.amethyst

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import dev.anthonyhfm.amethyst.home.Home
import dev.anthonyhfm.amethyst.ui.theme.AMETHYST_THEME
import dev.anthonyhfm.amethyst.workspace.Workspace
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    var inWorkspace by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = AMETHYST_THEME
    ) {
        if (inWorkspace) {
            Workspace()
        } else {
            Surface {
                Home(
                    onOpenWorkspace = {
                        inWorkspace = true
                    }
                )
            }
        }
    }
}