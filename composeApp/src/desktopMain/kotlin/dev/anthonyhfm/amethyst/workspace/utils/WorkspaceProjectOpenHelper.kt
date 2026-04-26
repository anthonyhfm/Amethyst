package dev.anthonyhfm.amethyst.workspace.utils

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
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
        val extensions = mutableListOf("ame", "als", "zip").apply {
            if (GlobalSettings.experimentalApolloConversionSupport) {
                add("approj")
            }
        }

        val file = FileKit.openFilePicker(
            type = FileKitType.File(extensions = extensions),
            title = "Open Project File"
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
                    append("Unable to open ")
                    append(file.path.substringAfterLast('/'))
                    append(".")
                },
                cause = cause
            )
        }
    }
}
