package dev.anthonyhfm.amethyst.home

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.MobileFileStorage
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.utils.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSData

import dev.anthonyhfm.amethyst.core.loading.ProjectLoadingManager
import io.github.vinceglb.filekit.path

/**
 * Bridge object exposing HomeRepository operations to native Swift code.
 *
 * File management: files picked via iOS document picker are copied into
 * <Documents>/Amethyst/ so they are persistently accessible and can be
 * stored in the recent-projects list.
 */
@OptIn(ExperimentalForeignApi::class)
object HomeSwiftBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun observeLoadingProgress(
        onUpdate: (Float, String, String, String?) -> Unit,
        onFinished: () -> Unit,
    ) {
        scope.launch {
            ProjectLoadingManager.loadingProgress.collect { report ->
                if (report != null) {
                    onUpdate(report.progress, report.title, report.statusText, report.detailText)
                } else {
                    onFinished()
                }
            }
        }
    }

    // ── File management ────────────────────────────────────────────────────

    /**
     * Writes [data] to persistent app storage, indexes the result
     * in [FileHelper], and returns the path of the stored file.
     */
    fun indexFile(data: NSData, filename: String): String {
        val bytes = data.toByteArray()
        val file = runBlocking { MobileFileStorage.copyBytesToPersistentStorage(bytes, filename) }
        return file.path
    }

    fun clearIndexedFile(path: String) {
        FileHelper.indexedFiles.remove(path)
    }

    // ── Synchronous accessors ──────────────────────────────────────────────

    fun recentWorkspaces(): List<RecentWorkspace> = HomeRepository.recentWorkspaces()

    fun removeRecentWorkspace(path: String) = HomeRepository.removeRecentWorkspace(path)

    fun localAuthor(): String = HomeRepository.localAuthor()

    /**
     * Returns the zip format as a string: "ABLETON", "ABLETON_APOLLO", or "UNIPAD".
     * Runs synchronously – call on a background thread when possible.
     */
    fun getZipFormat(path: String, onResult: (String) -> Unit) {
        scope.launch {
            val format = withContext(Dispatchers.IO) {
                val file = resolveFile(path)
                Zip.determineFormat(file).name
            }
            onResult(format)
        }
    }

    // ── Async project operations ───────────────────────────────────────────

    fun createProject(
        name: String,
        author: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { HomeRepository.createProject(name, author) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: getString(Res.string.home_swift_bridge_unknown_error)) }
        }
    }

    fun openWorkspaceFromPath(
        path: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val workspace = HomeRepository.loadWorkspaceData(resolveFile(path))
                HomeRepository.openWorkspace(workspace, rememberRecent = true)
            }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: getString(Res.string.home_swift_bridge_unknown_error)) }
        }
    }

    fun openRecentWorkspace(
        project: RecentWorkspace,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { HomeRepository.openRecentWorkspace(project) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: getString(Res.string.home_swift_bridge_unknown_error)) }
        }
    }

    fun importAbletonProject(
        path: String,
        palettePath: String?,
        apolloPath: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching {
                HomeRepository.importAbletonProject(
                    path = path,
                    customPalettePath = palettePath?.takeIf { it.isNotBlank() },
                    apolloProjPath = apolloPath?.takeIf { it.isNotBlank() },
                )
            }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: getString(Res.string.home_swift_bridge_unknown_error)) }
        }
    }

    fun updateProject(
        path: String,
        name: String,
        author: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            runCatching { HomeRepository.updateProject(path = path, name = name, author = author) }
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: getString(Res.string.home_swift_bridge_unknown_error)) }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun resolveFile(path: String): PlatformFile =
        MobileFileStorage.resolvePath(path)
}
