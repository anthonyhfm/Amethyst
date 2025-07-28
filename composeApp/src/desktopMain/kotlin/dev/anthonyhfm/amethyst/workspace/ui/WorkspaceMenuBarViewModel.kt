package dev.anthonyhfm.amethyst.workspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
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

    @OptIn(ExperimentalSerializationApi::class)
    fun saveProjectAs() {
        viewModelScope.launch {
            val file = FileKit.saveFile(
                bytes = AmethystProtoBuf.encodeToByteArray(
                    serializer = SaveableWorkspaceData.serializer(),
                    value = WorkspaceRepository.saveWorkspace()
                ),
                extension = "amproj",
                baseName = "project",
            )

            file?.readBytes()?.let { bytes ->
                val data = AmethystProtoBuf.decodeFromByteArray<SaveableWorkspaceData>(bytes).apply {
                    this.path = file.path
                }

                if (file.path != null) {
                    GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.plus(
                        RecentWorkspace(
                            title = data.title,
                            path = file.path!!
                        )
                    ).toSet().toList()
                }
            }
        }
    }

    fun closeProject() {
        
    }

    fun switchMode(mode: WorkspaceContract.WorkspaceMode) {
        WorkspaceRepository.switchMode(mode)
    }
}