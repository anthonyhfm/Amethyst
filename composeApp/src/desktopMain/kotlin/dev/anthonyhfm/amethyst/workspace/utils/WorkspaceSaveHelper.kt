package dev.anthonyhfm.amethyst.workspace.utils

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray

object WorkspaceSaveHelper {
    /**
     * Saves the current workspace, prompting for a file path if not already set.
     * Returns true if save was successful, false if cancelled.
     */
    @OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
    suspend fun saveWorkspace(): Boolean {
        var path = WorkspaceRepository.saveableWorkspaceData?.path

        if (path == null) {
            path = FileKit.openFileSaver(
                suggestedName = WorkspaceRepository.saveableWorkspaceData?.title ?: "Untitled",
                extension = "amproj"
            )?.path ?: return false
        }

        WorkspaceRepository.saveableWorkspaceData?.path = path

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
                        title = WorkspaceRepository.saveableWorkspaceData?.title ?: "Untitled",
                        path = path
                    )
                )
            }

        return true
    }
}
