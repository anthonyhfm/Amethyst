package dev.anthonyhfm.amethyst.workspace.utils

import org.jetbrains.compose.resources.getString
import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.path

sealed interface WorkspaceProjectOpenResult {
    data object Cancelled : WorkspaceProjectOpenResult

    data class Success(
        val projectTitle: String,
        val projectPath: String
    ) : WorkspaceProjectOpenResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : WorkspaceProjectOpenResult
}

object WorkspaceProjectOpenHelper {
    suspend fun openProjectPicker(): WorkspaceProjectOpenResult {
        val extensions = listOf("ame", "als", "zip", "rar", "approj")

        val file = FileKit.openFilePicker(
            type = FileKitType.File(extensions = extensions),
            title = getString(Res.string.workspace_open_dialog_title)
        ) ?: return WorkspaceProjectOpenResult.Cancelled

        return openProject(file)
    }

    suspend fun openRecentProject(project: RecentWorkspace): WorkspaceProjectOpenResult {
        return openProject(PlatformFile(project.path))
    }

    suspend fun openProject(file: PlatformFile): WorkspaceProjectOpenResult {
        return try {
            val workspace = HomeRepository.loadWorkspaceData(file)
            HomeRepository.openWorkspace(
                workspace = workspace,
                rememberRecent = true,
            )
            WorkspaceProjectOpenResult.Success(
                projectTitle = workspace.title,
                projectPath = file.path
            )
        } catch (cause: Exception) {
            WorkspaceProjectOpenResult.Failure(
                message = buildString {
                    append(getString(Res.string.workspace_open_error_prefix))
                    append(file.path.substringAfterLast('/'))
                    append(".")
                },
                cause = cause
            )
        }
    }
}
