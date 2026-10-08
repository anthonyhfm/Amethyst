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
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.core.util.determineProjectArchiveFormat
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.core.util.isMobile
import dev.anthonyhfm.amethyst.core.loading.ProjectLoadMetrics
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.workspace.chain.data.findMaxMacroIndex
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.decodeLegacyInlineAudioWorkspace
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
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
    private const val MOBILE_BUNDLE_VERSION = 1
    fun recentWorkspaces(): List<RecentWorkspace> {
        val recent = GlobalSettings.recentWorkspaces.map { it.copy(path = MobileFileStorage.resolvePath(it.path).path) }
        val recentByPath = recent.associateBy { it.path }
        val catalog = GlobalSettings.mobileProjects.map { project ->
            val path = MobileFileStorage.resolvePath(project.originalPath).path
            RecentWorkspace(
                title = project.title,
                path = path,
                lastOpened = maxOf(project.importedAt, recentByPath[path]?.lastOpened ?: 0L),
            )
        }
        return (catalog + recent.filterNot { item -> catalog.any { it.path == item.path } })
            .sortedByDescending { it.lastOpened }
    }

    fun registerMobileProject(record: MobileProjectRecord) {
        val previous = GlobalSettings.mobileProjects.firstOrNull { it.id == record.id }
        val updated = if (record.convertedPath == null && record.sourceHash != null &&
            previous != null && previous.sourceHash == record.sourceHash &&
            previous.converterVersion == MOBILE_BUNDLE_VERSION
        ) {
            record.copy(
                convertedPath = previous.convertedPath,
                convertedSourceHash = previous.convertedSourceHash,
                converterVersion = previous.converterVersion,
            )
        } else record
        GlobalSettings.mobileProjects = GlobalSettings.mobileProjects
            .filterNot { it.id == record.id || it.originalPath == record.originalPath } + updated
    }

    fun mobileProjectForPath(path: String): MobileProjectRecord? =
        GlobalSettings.mobileProjects.firstOrNull {
            MobileFileStorage.resolvePath(it.originalPath).path == path ||
                it.convertedPath?.let(MobileFileStorage::resolvePath)?.path == path
        }

    private fun preparedCacheRoot(path: String?): String? {
        val project = path?.let(::mobileProjectForPath) ?: return null
        val hash = project.sourceHash ?: return null
        val originalPath = MobileFileStorage.resolvePath(project.originalPath).path
        val projectRoot = originalPath.substringBeforeLast("/Original/", "")
        return if (projectRoot.isBlank()) null else "$projectRoot/Prepared/$hash"
    }

    fun hasConvertedMobileProject(path: String): Boolean = mobileProjectForPath(path)?.let {
        it.converterVersion == MOBILE_BUNDLE_VERSION &&
            it.convertedPath != null && it.convertedSourceHash == it.sourceHash
    } == true

    fun localAuthor(): String = GeneralSettings.localAuthor.value

    fun saveLocalAuthor(author: String) {
        val trimmed = author.trim()
        if (trimmed.isNotEmpty()) {
            GeneralSettings.localAuthor.update(trimmed)
        }
    }

    fun removeRecentWorkspace(path: String) {
        GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.filterNot {
            MobileFileStorage.resolvePath(it.path).path == path
        }
        GlobalSettings.mobileProjects = GlobalSettings.mobileProjects.filterNot {
            MobileFileStorage.resolvePath(it.originalPath).path == path
        }
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
            val mobileProject = mobileProjectForPath(file.path)
            val cached = mobileProject?.takeIf {
                it.converterVersion == MOBILE_BUNDLE_VERSION &&
                    it.convertedPath != null && it.convertedSourceHash == it.sourceHash
            }?.convertedPath?.let { ProjectLoadMetrics.measureSuspend("bundle.load") {
                MobileProjectBundle.load(MobileFileStorage.resolvePath(it).path)
            } }
            val workspace = cached ?: ProjectLoadMetrics.measure("source.convert") { when (file.extension.lowercase()) {
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
                "zip", "rar" -> when (determineProjectArchiveFormat(file)) {
                    dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat.ABLETON,
                    dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat.ABLETON_APOLLO -> {
                        AbletonConverter.convertZipToWorkspace(file)
                    }

                    dev.anthonyhfm.amethyst.core.util.ZippedProjectFormat.UNIPAD -> {
                        UnipadConverter.convertZipToWorkspace(file)
                    }
                }

                else -> error("Unsupported project file format: .${file.extension}")
            } }

            workspace.path = file.path
            if (cached == null) cacheMobileWorkspace(file.path, workspace)
            val loadingDevicesMsg = runCatching { getString(Res.string.home_loading_loading_devices) }.getOrDefault("Loading devices & chains...")
            dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                0.95f,
                statusText = loadingDevicesMsg,
                detailText = workspace.title
            )
            workspace
        }
    }

    private suspend fun cacheMobileWorkspace(originalPath: String, workspace: SavableWorkspaceData): Boolean {
        val record = mobileProjectForPath(originalPath) ?: return false
        val bundlePath = ProjectLoadMetrics.measureSuspend("bundle.save") {
            MobileProjectBundle.save(record.id, originalPath, workspace)
        } ?: return false
        registerMobileProject(record.copy(
            originalPath = originalPath,
            convertedPath = bundlePath,
            convertedSourceHash = record.sourceHash,
            converterVersion = MOBILE_BUNDLE_VERSION,
        ))
        return true
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun saveOpenMobileWorkspace(): Boolean = withContext(Dispatchers.Default) {
        val savedRevision = WorkspaceRepository.currentChangeRevision()
        val workspace = WorkspaceRepository.saveWorkspace()
        val path = workspace.path
        if (path != null && mobileProjectForPath(path) != null) {
            val saved = cacheMobileWorkspace(path, workspace)
            if (saved) {
                WorkspaceRepository.markSaved(savedRevision)
            }
            return@withContext saved
        }
        runCatching {
            val savedPath = saveLocalWorkspace(workspace)
            WorkspaceRepository.workspaceMeta = WorkspaceRepository.workspaceMeta?.copy(path = savedPath)
            WorkspaceRepository.markSaved(savedRevision)
            true
        }.getOrDefault(false)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun saveLocalWorkspace(workspace: SavableWorkspaceData): String {
        val bytes = Zip.encode(data = AmethystProtoBuf.encodeToByteArray(value = workspace))
        val path = workspace.path
        val file = if (path == null) {
            MobileFileStorage.copyBytesToPersistentStorage(bytes, "Local-${UUID.randomUUID()}.ame")
        } else {
            MobileFileStorage.resolvePath(path).also { it.write(bytes) }
        }
        rememberRecentWorkspace(title = workspace.title, path = file.path)
        return file.path
    }

    suspend fun openWorkspace(
        workspace: SavableWorkspaceData,
        rememberRecent: Boolean = false,
    ) {
        var installedChain: Chain? = null
        try {
            withContext(Dispatchers.Default) {
                ProjectLoadMetrics.measure("workspace.load") {
                    val loadContext = currentCoroutineContext()
                    loadContext.ensureActive()
                    WorkspaceRepository.loadWorkspace(
                        workspaceData = workspace,
                        preparedCacheRoot = preparedCacheRoot(workspace.path),
                        onPrepared = {
                            installedChain = WorkspaceRepository.lightsChain
                            loadContext.ensureActive()
                        },
                    )
                }

                val loadedMsg = runCatching { getString(Res.string.home_loading_project_loaded) }.getOrDefault("Project loaded!")
                dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                    1.0f,
                    statusText = loadedMsg,
                    detailText = workspace.title,
                )

                if (rememberRecent) {
                    workspace.path?.let { path ->
                        rememberRecentWorkspace(
                            title = workspace.title,
                            path = path,
                        )
                    }
                }
            }
        } catch (failure: Throwable) {
            installedChain?.let { chain ->
                try {
                    WorkspaceRepository.cleanIfCurrent(chain = chain)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(exception = cleanupFailure)
                }
            }
            throw failure
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

    @OptIn(ExperimentalSerializationApi::class)
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

            if (platform.isMobile) {
                workspace.path = saveLocalWorkspace(workspace)
            }
            saveLocalAuthor(author)
            val loadContext = currentCoroutineContext()
            loadContext.ensureActive()
            WorkspaceRepository.loadWorkspace(
                workspaceData = workspace,
                preparedCacheRoot = preparedCacheRoot(workspace.path),
                onPrepared = { loadContext.ensureActive() },
            )
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
        var installedChain: Chain? = null
        try {
            withContext(Dispatchers.Default) {
                val importedFile = resolveImportedFile(path)
                val workspace = when {
                    !apolloProjPath.isNullOrBlank() -> {
                        val abletonWorkspace = if (importedFile.isProjectArchive()) {
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

                    importedFile.isProjectArchive() -> {
                        AbletonConverter.convertZipToWorkspace(importedFile, palettePath = customPalettePath)
                    }

                    else -> {
                        AbletonConverter.convertToWorkspace(importedFile, customPalettePath)
                    }
                }

                workspace.path = importedFile.path
                cacheMobileWorkspace(importedFile.path, workspace)

                val loadingDevicesMsg = runCatching { getString(Res.string.home_loading_loading_devices) }.getOrDefault("Loading devices & chains...")
                dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                    0.95f,
                    statusText = loadingDevicesMsg,
                    detailText = workspace.title,
                )
                val loadContext = currentCoroutineContext()
                loadContext.ensureActive()
                WorkspaceRepository.loadWorkspace(
                    workspaceData = workspace,
                    preparedCacheRoot = preparedCacheRoot(workspace.path),
                    onPrepared = {
                        installedChain = WorkspaceRepository.lightsChain
                        loadContext.ensureActive()
                    },
                )
                val loadedMsg = runCatching { getString(Res.string.home_loading_project_loaded) }.getOrDefault("Project loaded!")
                dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager.reporter.update(
                    1.0f,
                    statusText = loadedMsg,
                    detailText = workspace.title,
                )
            }
        } catch (failure: Throwable) {
            installedChain?.let { chain ->
                try {
                    WorkspaceRepository.cleanIfCurrent(chain = chain)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(exception = cleanupFailure)
                }
            }
            throw failure
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun decodeAmethystWorkspace(file: PlatformFile): SavableWorkspaceData {
        val bytes = Zip.decode(file.readBytes())
        val workspace = try {
            AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(bytes = bytes)
        } catch (_: SerializationException) {
            decodeLegacyInlineAudioWorkspace(bytes)
        }
        workspace.path = file.path
        return workspace
    }

    private fun resolveImportedFile(path: String): PlatformFile {
        return MobileFileStorage.resolvePath(path)
    }

    private fun normalizeAuthor(author: String): String {
        return author.trim().ifBlank { "Unknown Author" }
    }

    private fun PlatformFile.isProjectArchive(): Boolean =
        extension.equals("zip", ignoreCase = true) || extension.equals("rar", ignoreCase = true)
}
