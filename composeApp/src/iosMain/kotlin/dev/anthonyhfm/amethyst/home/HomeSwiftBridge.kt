package dev.anthonyhfm.amethyst.home

import dev.anthonyhfm.amethyst.core.util.FileHelper
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.core.util.determineFormat
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

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

    // ── File management ────────────────────────────────────────────────────

    /**
     * Writes [data] to <Documents>/Amethyst/[filename], indexes the result
     * in [FileHelper], and returns the absolute path of the stored file.
     *
     * Call from Swift after reading the picked file's data (with security-
     * scoped access already started and stopped).
     */
    fun indexFile(data: NSData, filename: String): String {
        val documentsPath = documentsDirectory() ?: return ""
        val amethystDir = "$documentsPath/Amethyst"

        NSFileManager.defaultManager.createDirectoryAtPath(
            path = amethystDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val filePath = "$amethystDir/$filename"
        NSFileManager.defaultManager.createFileAtPath(
            path = filePath,
            contents = data,
            attributes = null,
        )
        FileHelper.indexedFiles[filePath] = PlatformFile(filePath)
        return filePath
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
                .onFailure { onError(it.message ?: "Unknown error") }
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
                .onFailure { onError(it.message ?: "Unknown error") }
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
                .onFailure { onError(it.message ?: "Unknown error") }
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
                .onFailure { onError(it.message ?: "Unknown error") }
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
                .onFailure { onError(it.message ?: "Unknown error") }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun resolveFile(path: String): PlatformFile =
        FileHelper.indexedFiles[path] ?: PlatformFile(path)

    private fun documentsDirectory(): String? {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
        return paths.firstOrNull() as? String
    }
}
