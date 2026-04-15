package dev.anthonyhfm.amethyst.workspace.utils

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray

object WorkspaceSaveHelper {
    /**
     * Saves the current workspace, prompting for a file path if not already set.
     * Returns true if save was successful, false if cancelled.
     */
    suspend fun saveWorkspace(): Boolean {
        var path = WorkspaceRepository.workspaceMeta?.path

        if (path == null) {
            path = FileKit.openFileSaver(
                suggestedName = WorkspaceRepository.workspaceMeta?.title ?: "Untitled",
                extension = "ame"
            )?.path ?: return false
        }

        return writeToPath(path)
    }

    /**
     * Always opens a file-save dialog, ignoring any existing path (Save As).
     * Returns true if save was successful, false if cancelled.
     */
    suspend fun saveWorkspaceAs(): Boolean {
        val path = FileKit.openFileSaver(
            suggestedName = WorkspaceRepository.workspaceMeta?.title ?: "Untitled",
            extension = "ame"
        )?.path ?: return false

        return writeToPath(path)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun writeToPath(rawPath: String): Boolean {
        val path = if (rawPath.endsWith(".ame")) rawPath else "$rawPath.ame"

        WorkspaceRepository.workspaceMeta = WorkspaceRepository.workspaceMeta?.copy(path = path)
            ?: WorkspaceRepository.workspaceMeta

        PlatformFile(path).write(
            bytes = Zip.encode(
                data = AmethystProtoBuf
                    .encodeToByteArray(
                        value = WorkspaceRepository.saveWorkspace()
                    )
            )
        )

        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces
            .filter { it.path != path }
            .toMutableList()
            .apply {
                add(
                    index = 0,
                    element = RecentWorkspace(
                        title = WorkspaceRepository.workspaceMeta?.title ?: "Untitled",
                        path = path
                    )
                )
            }

        return true
    }
}
