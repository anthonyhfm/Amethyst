package dev.anthonyhfm.amethyst.workspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenHelper
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenResult
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceSaveHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.swing.JOptionPane

class WorkspaceMenuBarViewModel : ViewModel() {
    private val _recentProjects = MutableStateFlow(loadRecentProjects())
    val recentProjects = _recentProjects.asStateFlow()

    fun openProject() {
        viewModelScope.launch {
            handleOpenResult(WorkspaceProjectOpenHelper.openProjectPicker())
        }
    }

    fun openRecentProject(project: RecentWorkspace) {
        viewModelScope.launch {
            handleOpenResult(WorkspaceProjectOpenHelper.openRecentProject(project))
        }
    }

    fun saveProject() {
        viewModelScope.launch {
            if (WorkspaceSaveHelper.saveWorkspace()) {
                refreshRecentProjects()
            }
        }
    }

    fun saveProjectAs() {
        viewModelScope.launch {
            if (WorkspaceSaveHelper.saveWorkspaceAs()) {
                refreshRecentProjects()
            }
        }
    }

    fun switchMode(mode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode) {
        dev.anthonyhfm.amethyst.workspace.WorkspaceRepository.switchMode(mode)
    }

    private fun refreshRecentProjects() {
        _recentProjects.value = loadRecentProjects()
    }

    private fun handleOpenResult(result: WorkspaceProjectOpenResult) {
        when (result) {
            is WorkspaceProjectOpenResult.Cancelled -> Unit
            is WorkspaceProjectOpenResult.Success -> refreshRecentProjects()
            is WorkspaceProjectOpenResult.Failure -> {
                JOptionPane.showMessageDialog(
                    null,
                    result.message,
                    "Open Project Failed",
                    JOptionPane.ERROR_MESSAGE
                )
                result.cause?.printStackTrace()
            }
        }
    }

    private fun loadRecentProjects(): List<RecentWorkspace> {
        return HomeRepository.recentWorkspaces()
    }
}
