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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.JOptionPane

object WorkspaceSaveHelper {
    enum class SavePhase {
        Preparing,
        Compressing,
        Writing,
        Finishing,
    }

    data class SaveProgress(
        val destination: String,
        val phase: SavePhase,
        val progress: Float,
    )

    private const val WRITE_CHUNK_SIZE = 1024 * 1024
    private val saveMutex = Mutex()
    private val _saveProgress = MutableStateFlow<SaveProgress?>(null)

    val saveProgress = _saveProgress.asStateFlow()
    val isSaving: Boolean get() = saveMutex.isLocked

    /**
     * Saves the current workspace, prompting for a file path if not already set.
     * Returns true if save was successful, false if cancelled.
     */
    suspend fun saveWorkspace(): Boolean {
        if (!saveMutex.tryLock()) return false

        try {
            var path = WorkspaceRepository.workspaceMeta?.path

            if (path == null) {
                path = FileKit.openFileSaver(
                    suggestedName = WorkspaceRepository.workspaceMeta?.title
                        ?: getString(Res.string.workspace_save_untitled),
                    extension = "ame"
                )?.path ?: return false
            }

            return writeToPath(path)
        } finally {
            saveMutex.unlock()
        }
    }

    /**
     * Always opens a file-save dialog, ignoring any existing path (Save As).
     * Returns true if save was successful, false if cancelled.
     */
    suspend fun saveWorkspaceAs(): Boolean {
        if (!saveMutex.tryLock()) return false

        try {
            val path = FileKit.openFileSaver(
                suggestedName = WorkspaceRepository.workspaceMeta?.title
                    ?: getString(Res.string.workspace_save_untitled),
                extension = "ame"
            )?.path ?: return false

            return writeToPath(path)
        } finally {
            saveMutex.unlock()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun writeToPath(rawPath: String): Boolean {
        val path = if (rawPath.endsWith(".ame", ignoreCase = true)) rawPath else "$rawPath.ame"
        val destination = Paths.get(path).toAbsolutePath().normalize().toString()
        updateProgress(destination, SavePhase.Preparing, 0.05f)

        val result = try {
            runCatching {
                // Give Compose a chance to display the modal before the workspace snapshot is built.
                yield()
                val workspace = withContext(Dispatchers.Default) {
                    WorkspaceRepository.saveWorkspace()
                }

                updateProgress(destination, SavePhase.Compressing, 0.30f)
                val bytes = withContext(Dispatchers.Default) {
                    Zip.encode(
                        data = AmethystProtoBuf.encodeToByteArray(value = workspace)
                    )
                }

                updateProgress(destination, SavePhase.Writing, 0.65f)
                withContext(Dispatchers.IO) {
                    val outputPath = Paths.get(path)
                    outputPath.parent?.let { Files.createDirectories(it) }
                    Files.newOutputStream(outputPath).buffered().use { output ->
                        var offset = 0
                        while (offset < bytes.size) {
                            val count = minOf(WRITE_CHUNK_SIZE, bytes.size - offset)
                            output.write(bytes, offset, count)
                            offset += count
                            val writtenFraction = if (bytes.isEmpty()) 1f else offset.toFloat() / bytes.size
                            updateProgress(
                                destination = destination,
                                phase = SavePhase.Writing,
                                progress = 0.65f + writtenFraction * 0.30f,
                            )
                        }
                    }
                }

                updateProgress(destination, SavePhase.Finishing, 0.98f)
                WorkspaceRepository.workspaceMeta = WorkspaceRepository.workspaceMeta?.copy(path = path)
                    ?: WorkspaceRepository.workspaceMeta

                HomeRepository.rememberRecentWorkspace(
                    title = WorkspaceRepository.workspaceMeta?.title
                        ?: getString(Res.string.workspace_save_untitled),
                    path = path,
                )

                updateProgress(destination, SavePhase.Finishing, 1f)
                true
            }
        } finally {
            _saveProgress.value = null
        }

        result.exceptionOrNull()?.let { cause ->
            if (cause is CancellationException) throw cause
        }

        return result.getOrElse { cause ->
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

    private fun updateProgress(destination: String, phase: SavePhase, progress: Float) {
        _saveProgress.value = SaveProgress(
            destination = destination,
            phase = phase,
            progress = progress.coerceIn(0f, 1f),
        )
    }
}
