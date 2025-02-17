package dev.anthonyhfm.amethyst.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar

@Composable
fun FrameWindowScope.EditorMenuBar() {
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