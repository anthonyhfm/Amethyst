package dev.anthonyhfm.amethyst.home.data

import dev.anthonyhfm.amethyst.conversion.ableton.AbletonConverter
import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.conversion.unipad.UnipadConverter
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.settings.data.GeneralSettings
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.MobileFileStorage
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.findMaxMacroIndex
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
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

    fun localAuthor(): String = GeneralSettings.localAuthor.value

    fun saveLocalAuthor(author: String) {
        val trimmed = author.trim()
        if (trimmed.isNotEmpty()) {
            GeneralSettings.localAuthor.update(trimmed)
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
                "ame" -> {
                    val decodingMsg = runCatching { getString(Res.string.home_loading_decoding_ame) }.getOrDefault("Decoding Amethyst project...")
                    dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                        0.3f,
                        statusText = decodingMsg,
                        detailText = file.name
                    )
                    val decoded = decodeAmethystWorkspace(file)
                    val loadingDevicesMsg = runCatching { getString(Res.string.home_loading_loading_devices) }.getOrDefault("Loading devices & chains...")
                    dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                        0.9f,
                        statusText = loadingDevicesMsg,
                        detailText = decoded.title
                    )
                    decoded
                }
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
            val loadedMsg = runCatching { getString(Res.string.home_loading_project_loaded) }.getOrDefault("Project loaded!")
            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                1.0f,
                statusText = loadedMsg,
                detailText = workspace.title
            )
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
        val file = MobileFileStorage.resolvePath(project.path)
        val workspace = loadWorkspaceData(file)
        openWorkspace(
            workspace = workspace,
            rememberRecent = true,
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun loadProjectDetails(path: String): HomeProjectDetails? {
        return withContext(Dispatchers.Default) {
            runCatching {
                val workspace = decodeAmethystWorkspace(MobileFileStorage.resolvePath(path))
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
                    SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                        positionX = 0f,
                        positionY = 0f
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
            val file = MobileFileStorage.resolvePath(path)
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
                        AbletonConverter.convertZipToWorkspace(importedFile, palettePath = customPalettePath)
                    } else {
                        AbletonConverter.convertToWorkspace(importedFile, customPalettePath)
                    }

                    val apolloWorkspace = ApolloConverter.convertToWorkspace(
                        apolloProjPath,
                        palettePath = null,
                    )
                    val maxMacroIndex = maxOf(
                        apolloWorkspace.lights.findMaxMacroIndex(),
                        abletonWorkspace.sampling.findMaxMacroIndex(),
                        abletonWorkspace.lights.findMaxMacroIndex()
                    )
                    val macroCount = maxOf(maxMacroIndex + 1, apolloWorkspace.macros.size, abletonWorkspace.macros.size, 1)
                    val mergedMacros = List(macroCount) { idx ->
                        apolloWorkspace.macros.getOrNull(idx)
                            ?: abletonWorkspace.macros.getOrNull(idx)
                            ?: Macro(0)
                    }
                    abletonWorkspace.copy(
                        lights = apolloWorkspace.lights,
                        launchpadDevices = apolloWorkspace.launchpadDevices.ifEmpty { abletonWorkspace.launchpadDevices },
                        macros = mergedMacros
                    )
                }

                importedFile.extension.equals("zip", ignoreCase = true) -> {
                    AbletonConverter.convertZipToWorkspace(importedFile, palettePath = customPalettePath)
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
        return MobileFileStorage.resolvePath(path)
    }

    private fun normalizeAuthor(author: String): String {
        return author.trim().ifBlank { "Unknown Author" }
    }
}
