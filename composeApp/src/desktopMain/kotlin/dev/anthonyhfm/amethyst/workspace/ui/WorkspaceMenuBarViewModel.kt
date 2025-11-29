package dev.anthonyhfm.amethyst.workspace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
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
                Zip.encode(
                    AmethystProtoBuf.encodeToByteArray(
                        serializer = SavableWorkspaceData.serializer(),
                        value = workspace
                    )
                )
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun saveProjectAs() {
        viewModelScope.launch {
            val file = FileKit.openFileSaver(
                extension = "amproj",
                suggestedName = "project",
            )

            file?.write(
                AmethystProtoBuf.encodeToByteArray(
                    serializer = SavableWorkspaceData.serializer(),
                    value = WorkspaceRepository.saveWorkspace()
                )
            )

            file?.readBytes()?.let { bytes ->
                val data = AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(bytes).apply {
                    this.path = file.path
                }

                GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.plus(
                    RecentWorkspace(
                        title = data.title,
                        path = file.path
                    )
                ).toSet().toList()
            }
        }
    }

    fun closeProject() {
        
    }

    fun switchMode(mode: WorkspaceContract.WorkspaceMode) {
        WorkspaceRepository.switchMode(mode)
    }
}