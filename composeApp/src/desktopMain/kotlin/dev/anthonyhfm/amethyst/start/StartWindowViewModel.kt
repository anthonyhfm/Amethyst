package dev.anthonyhfm.amethyst.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
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
            val file = FileKit.openFileSaver(
                extension = "amproj",
                suggestedName = "project",
            )

            file?.write(
                AmethystProtoBuf.encodeToByteArray(
                    serializer = SaveableWorkspaceData.serializer(),
                    value = SaveableWorkspaceData("New Project")
                )
            )

            file?.readBytes()?.let { bytes ->
                val data = AmethystProtoBuf.decodeFromByteArray<SaveableWorkspaceData>(bytes).apply {
                    this.path = file.path
                }

                GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.plus(
                    RecentWorkspace(
                        title = data.title,
                        path = file.path
                    )
                ).toSet().toList()

                WorkspaceRepository.loadWorkspace(data)

                onOpenEditor?.invoke()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun onClickOpenProject() {
        viewModelScope.launch {
            val file = FileKit.openFilePicker(
                type = FileKitType.File(
                    extensions = listOf(
                        "amproj",
                        "zip",
                        "als",
                        "approj"
                    )
                ),
                mode = FileKitMode.Single,
            )

            when (file?.extension?.lowercase()) {
                "amproj" -> {
                    openProjectFile(file.path)
                }

                "als" -> {
                    WorkspaceRepository.loadWorkspace(
                        workspaceData = AbletonConverter.convertToWorkspace(file.path)
                    )

                    onOpenEditor?.invoke()
                }

                "zip" -> {
                    WorkspaceRepository.loadWorkspace(
                        workspaceData = UnipadConverter.convertToWorkspace(file.path)
                    )

                    onOpenEditor?.invoke()
                }
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

    fun onRemoveProjectFromRecents(recentWorkspace: RecentWorkspace) {
        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.filterNot {
            it.path == recentWorkspace.path
        }
    }
}