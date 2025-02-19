package dev.anthonyhfm.amethyst.workspace.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar

@Composable
fun FrameWindowScope.WorkspaceMenuBar() {
    MenuBar {
        Menu(
            text = "File"
        ) {

        }

        Menu(
            text = "Edit"
        ) {

        }

        Menu(
            text = "View"
        ) {

        }
    }
}