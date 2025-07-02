package dev.anthonyhfm.amethyst.workspace.ui

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

@Composable
fun FrameWindowScope.WorkspaceMenuBar() {
    val viewModel = viewModel { WorkspaceMenuBarViewModel() }

    MenuBar {
        Menu(
            text = "File"
        ) {
            Item(
                text = "Open Project",
                onClick = {
                    viewModel.openProject()
                }
            )

            Separator()

            Item(
                text = "Save",
                onClick = {
                    viewModel.saveProject()
                }
            )

            Item(
                text = "Save As..",
                onClick = {
                    viewModel.saveProjectAs()
                }
            )

            Separator()

            Item(
                text = "Close Project",
                onClick = {
                    viewModel.closeProject()
                }
            )
        }

        Menu(
            text = "View"
        ) {
            Menu(
                text = "Workspace Mode"
            ) {
                Item("Layout") {
                    viewModel.switchMode(WorkspaceContract.WorkspaceMode.Layout())
                }

                Item("Preview") {
                    viewModel.switchMode(WorkspaceContract.WorkspaceMode.Preview())
                }

                Item("Lights (Chain-Editor)") {
                    viewModel.switchMode(WorkspaceContract.WorkspaceMode.LightsChain())
                }

                Item("Sampling (Chain-Editor)") {
                    viewModel.switchMode(WorkspaceContract.WorkspaceMode.SamplingChain())
                }
            }
        }
    }
}