package dev.anthonyhfm.amethyst.workspace.utils

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
        return withContext(Dispatchers.Default) {
            try {
                val workspace = loadWorkspaceData(file)
                WorkspaceRepository.loadWorkspace(workspace)
                touchRecentWorkspace(title = workspace.title, path = file.path)
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

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun loadWorkspaceData(file: PlatformFile): SavableWorkspaceData {
        val extension = file.extension.lowercase()

        val workspace = when (extension) {
            "ame" -> {
                AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(
                    bytes = Zip.decode(file.readBytes())
                )
            }

            "als" -> {
                AbletonConverter.convertToWorkspace(file, palettePath = null)
            }

            "approj" -> {
                ApolloConverter.convertFileToWorkspace(file)
            }

            "zip" -> {
                when (Zip.determineFormat(file)) {
                    ZippedProjectFormat.ABLETON,
                    ZippedProjectFormat.ABLETON_APOLLO -> AbletonConverter.convertZipToWorkspace(file)

                    ZippedProjectFormat.UNIPAD -> UnipadConverter.convertZipToWorkspace(file)
                }
            }

            else -> error("Unsupported project file format: .$extension")
        }

        workspace.path = file.path
        return workspace
    }

    @OptIn(ExperimentalTime::class)
    private fun touchRecentWorkspace(title: String, path: String) {
        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces
            .filter { it.path != path }
            .toMutableList()
            .apply {
                add(
                    index = 0,
                    element = RecentWorkspace(
                        title = title.ifBlank { "Untitled Workspace" },
                        path = path,
                        lastOpened = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
    }
}
