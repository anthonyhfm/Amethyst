package dev.anthonyhfm.amethyst.workspace.ui

import androidx.lifecycle.ViewModel
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

class WorkspaceMenuBarViewModel : ViewModel() {
    fun openProject() {
        // Logic to open a project
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun saveProject() {
        val workspace = WorkspaceRepository.saveWorkspace()

        File(workspace.path).let { file ->
            file.writeBytes(
                AmethystProtoBuf.encodeToByteArray(
                    serializer = SaveableWorkspaceData.serializer(),
                    value = workspace
                )
            )
        }
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