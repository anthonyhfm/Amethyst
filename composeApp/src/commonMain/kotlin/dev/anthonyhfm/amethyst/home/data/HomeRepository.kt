package dev.anthonyhfm.amethyst.home.data

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class HomeProjectDetails(
    val name: String,
    val author: String,
    val projectPath: String? = null,
)

object HomeRepository {
    fun recentWorkspaces(): List<RecentWorkspace> {
        return GlobalSettings.recentWorkspaces.sortedByDescending { it.lastOpened }
    }

    fun localAuthor(): String = GlobalSettings.localAuthor

    fun saveLocalAuthor(author: String) {
        val trimmed = author.trim()
        if (trimmed.isNotEmpty()) {
            GlobalSettings.localAuthor = trimmed
        }
    }

    fun removeRecentWorkspace(path: String) {
        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.filterNot { it.path == path }
    }

    @OptIn(ExperimentalTime::class)
    fun rememberRecentWorkspace(
        title: String,
        path: String,
        lastOpened: Long = Clock.System.now().toEpochMilliseconds(),
    ) {
        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces
            .filter { it.path != path }
            .toMutableList()
            .apply {
                add(
                    index = 0,
                    element = RecentWorkspace(
                        title = title.ifBlank { "Untitled Workspace" },
                        path = path,
                        lastOpened = lastOpened,
                    )
                )
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun loadWorkspaceData(file: PlatformFile): SavableWorkspaceData {
        return withContext(Dispatchers.Default) {
            val workspace = when (file.extension.lowercase()) {
                "ame" -> decodeAmethystWorkspace(file)
                "als" -> AbletonConverter.convertToWorkspace(file, palettePath = null)
                "approj" -> ApolloConverter.convertFileToWorkspace(file)
                "zip" -> when (Zip.determineFormat(file)) {
                    dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat.ABLETON,
                    dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat.ABLETON_APOLLO -> {
                        AbletonConverter.convertZipToWorkspace(file)
                    }

                    dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat.UNIPAD -> {
                        UnipadConverter.convertZipToWorkspace(file)
                    }
                }

                else -> error("Unsupported project file format: .${file.extension}")
            }

            workspace.path = file.path
            workspace
        }
    }

    suspend fun openWorkspace(
        workspace: SavableWorkspaceData,
        rememberRecent: Boolean = false,
    ) {
        withContext(Dispatchers.Default) {
            WorkspaceRepository.loadWorkspace(workspace)

            if (rememberRecent) {
                workspace.path?.let { path ->
                    rememberRecentWorkspace(
                        title = workspace.title,
                        path = path,
                    )
                }
            }
        }
    }

    suspend fun openRecentWorkspace(project: RecentWorkspace) {
        val workspace = loadWorkspaceData(PlatformFile(project.path))
        openWorkspace(
            workspace = workspace,
            rememberRecent = true,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun loadProjectDetails(path: String): HomeProjectDetails? {
        return withContext(Dispatchers.Default) {
            runCatching {
                val workspace = decodeAmethystWorkspace(PlatformFile(path))
                HomeProjectDetails(
                    name = workspace.title,
                    author = workspace.author,
                    projectPath = path,
                )
            }.getOrNull()
        }
    }

    suspend fun createProject(
        name: String,
        author: String,
    ) {
        withContext(Dispatchers.Default) {
            val workspace = SavableWorkspaceData(
                title = name.trim(),
                author = normalizeAuthor(author),
                launchpadDevices = listOf(
                    SavableWorkspaceData.SavableViewportLaunchpad(
                        positionX = 0f,
                        positionY = 0f,
                        type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO,
                    )
                )
            )

            saveLocalAuthor(author)
            WorkspaceRepository.loadWorkspace(workspace)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun updateProject(
        path: String,
        name: String,
        author: String,
    ) {
        withContext(Dispatchers.Default) {
            val file = PlatformFile(path)
            val existingWorkspace = decodeAmethystWorkspace(file)
            val updatedWorkspace = existingWorkspace.copy(
                title = name.trim(),
                author = normalizeAuthor(author),
            ).apply {
                this.path = path
            }

            file.write(
                bytes = Zip.encode(
                    data = AmethystProtoBuf.encodeToByteArray(
                        value = updatedWorkspace,
                    )
                )
            )

            rememberRecentWorkspace(
                title = updatedWorkspace.title,
                path = path,
            )
            saveLocalAuthor(author)
            WorkspaceRepository.loadWorkspace(updatedWorkspace)
        }
    }

    suspend fun importAbletonProject(
        path: String,
        customPalettePath: String?,
        apolloProjPath: String?,
    ) {
        withContext(Dispatchers.Default) {
            val importedFile = resolveImportedFile(path)
            val workspace = when {
                !apolloProjPath.isNullOrBlank() -> {
                    val abletonWorkspace = if (importedFile.extension.equals("zip", ignoreCase = true)) {
                        AbletonConverter.convertZipToWorkspace(importedFile)
                    } else {
                        AbletonConverter.convertToWorkspace(importedFile, customPalettePath)
                    }

                    val apolloWorkspace = ApolloConverter.convertToWorkspace(
                        apolloProjPath,
                        palettePath = null,
                    )
                    abletonWorkspace.copy(lights = apolloWorkspace.lights)
                }

                importedFile.extension.equals("zip", ignoreCase = true) -> {
                    AbletonConverter.convertZipToWorkspace(importedFile)
                }

                else -> {
                    AbletonConverter.convertToWorkspace(importedFile, customPalettePath)
                }
            }

            WorkspaceRepository.loadWorkspace(workspace)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun decodeAmethystWorkspace(file: PlatformFile): SavableWorkspaceData {
        val workspace = AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(
            bytes = Zip.decode(file.readBytes())
        )
        workspace.path = file.path
        return workspace
    }

    private fun resolveImportedFile(path: String): PlatformFile {
        return if (platform is Platform.Android || platform is Platform.iOS) {
            FileHelper.indexedFiles[path] ?: PlatformFile(path)
        } else {
            PlatformFile(path)
        }
    }

    private fun normalizeAuthor(author: String): String {
        return author.trim().ifBlank { "Unknown Author" }
    }
}
