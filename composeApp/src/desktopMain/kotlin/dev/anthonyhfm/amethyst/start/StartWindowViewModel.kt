package dev.anthonyhfm.amethyst.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.awt.Desktop
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
                bytes = ProtoBuf.encodeToByteArray(
                    serializer = SaveableWorkspaceData.serializer(),
                    value = SaveableWorkspaceData("New Project", "")
                ),
                extension = "aspj",
                baseName = "project",
            )

            file?.readBytes()?.let { bytes ->
                // amethystReader.readFromFile(bytes)
            }

            onOpenEditor?.invoke()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun onClickOpenProject() {
        viewModelScope.launch {
            val file = FileKit.pickFile(
                type = PickerType.File(
                    extensions = listOf(
                        "aspj",
                    )
                ),
                mode = PickerMode.Single,
            )

            file?.readBytes()?.let { bytes ->
                // amethystReader.readFromFile(bytes)
            }

            onOpenEditor?.invoke()
        }
    }
}