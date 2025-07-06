package dev.anthonyhfm.amethyst.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.devices.DeviceStateSerializationModule
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
import java.awt.Desktop
import java.io.File
import java.net.URI

class StartWindowViewModel() : ViewModel() {
    var onOpenEditor: (() -> Unit)? = null

    fun openGitHubWebsite() {
        Desktop.getDesktop().browse(
            URI("https://github.com/anthonyhfm/amethyst")
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun onClickCreateProject() {
        viewModelScope.launch {
            val file = FileKit.saveFile(
                bytes = AmethystProtoBuf.encodeToByteArray(
                    serializer = SaveableWorkspaceData.serializer(),
                    value = SaveableWorkspaceData("New Project")
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

                WorkspaceRepository.loadWorkspace(data)

                onOpenEditor?.invoke()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun onClickOpenProject() {
        viewModelScope.launch {
            val file = FileKit.pickFile(
                type = PickerType.File(
                    extensions = listOf(
                        "amproj",
                    )
                ),
                mode = PickerMode.Single,
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

                WorkspaceRepository.loadWorkspace(data)

                onOpenEditor?.invoke()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun openProjectFile(path: String) {
        viewModelScope.launch {
            val file = File(path)

            file.readBytes().let { bytes ->
                val data = AmethystProtoBuf.decodeFromByteArray<SaveableWorkspaceData>(bytes).apply {
                    this.path = file.path
                }

                if (file.path != null) {
                    GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.plus(
                        RecentWorkspace(
                            title = data.title,
                            path = file.path
                        )
                    ).toSet().toList()
                }

                WorkspaceRepository.loadWorkspace(data)

                onOpenEditor?.invoke()
            }
        }
    }
}