package dev.anthonyhfm.amethyst.workspace.ui

import androidx.lifecycle.ViewModel
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

class WorkspaceMenuBarViewModel : ViewModel() {
    fun openProject() {
        // Logic to open a project
    }

    fun saveProject() {
        // Logic to save the current project
    }

    fun saveProjectAs() {
        // Logic to save the current project as a new file
    }

    fun closeProject() {
        // Logic to close the current project
    }

    fun switchMode(mode: WorkspaceContract.WorkspaceMode) {
        WorkspaceRepository.switchMode(mode)
    }
}