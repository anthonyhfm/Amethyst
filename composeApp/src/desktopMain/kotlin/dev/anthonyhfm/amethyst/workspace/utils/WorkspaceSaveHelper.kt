package dev.anthonyhfm.amethyst.workspace.utils

import org.jetbrains.compose.resources.getString
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JOptionPane

object WorkspaceSaveHelper {
    /**
     * Saves the current workspace, prompting for a file path if not already set.
     * Returns true if save was successful, false if cancelled.
     */
    suspend fun saveWorkspace(): Boolean {
        var path = WorkspaceRepository.workspaceMeta?.path

        if (path == null) {
            path = FileKit.openFileSaver(
                suggestedName = WorkspaceRepository.workspaceMeta?.title ?: getString(Res.string.workspace_save_untitled),
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
            suggestedName = WorkspaceRepository.workspaceMeta?.title ?: getString(Res.string.workspace_save_untitled),
            extension = "ame"
        )?.path ?: return false

        return writeToPath(path)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun writeToPath(rawPath: String): Boolean {
        val path = if (rawPath.endsWith(".ame", ignoreCase = true)) rawPath else "$rawPath.ame"
        val bytes = Zip.encode(
            data = AmethystProtoBuf
                .encodeToByteArray(
                    value = WorkspaceRepository.saveWorkspace()
                )
        )

        return runCatching {
            withContext(Dispatchers.IO) {
                val outputPath = Paths.get(path)
                outputPath.parent?.let { Files.createDirectories(it) }
                Files.write(outputPath, bytes)
            }

            WorkspaceRepository.workspaceMeta = WorkspaceRepository.workspaceMeta?.copy(path = path)
                ?: WorkspaceRepository.workspaceMeta

            HomeRepository.rememberRecentWorkspace(
                title = WorkspaceRepository.workspaceMeta?.title ?: getString(Res.string.workspace_save_untitled),
                path = path,
            )

            true
        }.getOrElse { cause ->
            cause.printStackTrace()
            JOptionPane.showMessageDialog(
                null,
                "Unable to save workspace:\n${cause.message ?: cause::class.simpleName}",
                getString(Res.string.workspace_save_failed_title),
                JOptionPane.ERROR_MESSAGE
            )
            false
        }
    }
}
