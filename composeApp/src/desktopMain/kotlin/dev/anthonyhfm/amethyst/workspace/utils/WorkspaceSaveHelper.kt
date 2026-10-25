package dev.anthonyhfm.amethyst.workspace.utils

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_save_failed_title
import amethyst.composeapp.generated.resources.workspace_save_untitled
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import java.nio.file.Paths
import javax.swing.JOptionPane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

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

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _saveProgress = MutableStateFlow<SaveProgress?>(null)

    val saveProgress = _saveProgress.asStateFlow()
    val isSaving: Boolean get() = _saveProgress.value != null

    suspend fun saveWorkspace(): Boolean = requestSave(choosePath = false)

    suspend fun saveWorkspaceAs(): Boolean = requestSave(choosePath = true)

    suspend fun awaitPendingSaves() {
        saveProgress.first { it == null }
    }

    private suspend fun requestSave(choosePath: Boolean): Boolean = withContext(Dispatchers.Main.immediate) {
        if (isSaving) {
            return@withContext false
        }

        saveScope.async(start = CoroutineStart.UNDISPATCHED) {
            try {
                updateProgress(
                    destination = WorkspaceRepository.workspaceMeta?.path.orEmpty(),
                    phase = SavePhase.Preparing,
                    progress = 0.02f,
                )
                val workspaceChain = WorkspaceRepository.lightsChain
                val workspace = WorkspaceRepository.saveWorkspace()
                val snapshot = WorkspaceSaveSnapshot.capture(
                    workspace = workspace,
                    revision = WorkspaceRepository.currentChangeRevision(),
                )
                val requestedPath = workspace.path

                val existingPath = requestedPath ?: WorkspaceRepository.workspaceMeta?.path
                val rawPath = if (choosePath || existingPath == null) {
                    FileKit.openFileSaver(
                        suggestedName = snapshot.title.ifBlank {
                            getString(resource = Res.string.workspace_save_untitled)
                        },
                        defaultExtension = "ame",
                    )?.path ?: return@async false
                } else {
                    existingPath
                }
                writeToPath(rawPath = rawPath, snapshot = snapshot, workspaceChain = workspaceChain)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                cause.printStackTrace()
                JOptionPane.showMessageDialog(
                    null,
                    "Unable to save workspace:\n${cause.message ?: cause::class.simpleName}",
                    getString(Res.string.workspace_save_failed_title),
                    JOptionPane.ERROR_MESSAGE,
                )
                false
            } finally {
                _saveProgress.value = null
            }
        }.await()
    }

    private suspend fun writeToPath(
        rawPath: String,
        snapshot: WorkspaceSaveSnapshot,
        workspaceChain: Chain,
    ): Boolean {
        val path = if (rawPath.endsWith(suffix = ".ame", ignoreCase = true)) rawPath else "$rawPath.ame"
        val outputPath = Paths.get(path).toAbsolutePath().normalize()
        val destination = outputPath.toString()
        updateProgress(destination = destination, phase = SavePhase.Compressing, progress = 0.05f)

        withContext(Dispatchers.IO) {
            writeWorkspaceAtomically(destination = outputPath) { output ->
                snapshot.writeTo(output = output) { fraction ->
                    updateProgress(
                        destination = destination,
                        phase = SavePhase.Compressing,
                        progress = 0.05f + fraction * 0.90f,
                    )
                }
                updateProgress(destination = destination, phase = SavePhase.Writing, progress = 0.96f)
            }
        }

        updateProgress(destination = destination, phase = SavePhase.Finishing, progress = 0.99f)
        if (WorkspaceRepository.lightsChain === workspaceChain) {
            WorkspaceRepository.workspaceMeta = WorkspaceRepository.workspaceMeta?.copy(path = destination)
            WorkspaceRepository.markSaved(revision = snapshot.revision)
        }
        withContext(Dispatchers.IO) {
            runCatching {
                HomeRepository.rememberRecentWorkspace(title = snapshot.title, path = destination)
            }.onFailure { cause ->
                cause.printStackTrace()
            }
        }
        return true
    }

    private fun updateProgress(destination: String, phase: SavePhase, progress: Float) {
        _saveProgress.value = SaveProgress(
            destination = destination,
            phase = phase,
            progress = progress.coerceIn(minimumValue = 0f, maximumValue = 1f),
        )
    }
}
