package dev.anthonyhfm.amethyst.home.ui.views

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.core.util.Zip
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ProjectCreationDialogViewModel(
    private val projectPath: String? = null
) : BaseViewModel<ProjectCreationDialogContract.State, ProjectCreationDialogContract.Event, ProjectCreationDialogContract.Effect>(
    initialState = if (projectPath != null) {
        // Load existing project data for editing
        loadProjectData(projectPath)
    } else {
        ProjectCreationDialogContract.State(
            name = "",
            author = GlobalSettings.localAuthor,
            projectPath = null
        )
    }
) {

    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        private fun loadProjectData(path: String): ProjectCreationDialogContract.State {
            return try {
                val file = PlatformFile(path)
                val workspace = runBlocking {
                    AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(
                        bytes = Zip.decode(file.readBytes())
                    )
                }
                ProjectCreationDialogContract.State(
                    name = workspace.title,
                    author = workspace.author,
                    isNameValid = workspace.title.isNotBlank(),
                    projectPath = path
                )
            } catch (e: Exception) {
                ProjectCreationDialogContract.State(
                    name = "",
                    author = GlobalSettings.localAuthor,
                    projectPath = null
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    override fun onEvent(event: ProjectCreationDialogContract.Event) {
        when (event) {
            is ProjectCreationDialogContract.Event.OnClickCreateProject -> {
                if (!state.value.isNameValid) return

                if (state.value.projectPath != null) {
                    // Edit mode: Update existing project
                    val file = PlatformFile(state.value.projectPath!!)
                    val existingWorkspace = runBlocking {
                        AmethystProtoBuf.decodeFromByteArray<SavableWorkspaceData>(
                            bytes = Zip.decode(file.readBytes())
                        )
                    }
                    
                    val updatedWorkspace = existingWorkspace.copy(
                        title = state.value.name.trim(),
                        author = state.value.author.trim().ifBlank { "Unknown Author" }
                    ).apply {
                        path = state.value.projectPath
                    }
                    
                    // Save the updated workspace
                    file.write(
                        bytes = Zip.encode(
                            data = AmethystProtoBuf.encodeToByteArray(
                                serializer = SavableWorkspaceData.serializer(),
                                value = updatedWorkspace
                            )
                        )
                    )
                    
                    // Update recent workspaces list
                    GlobalSettings.recentWorkspaces = GlobalSettings.recentWorkspaces.map {
                        if (it.path == state.value.projectPath) {
                            it.copy(
                                title = state.value.name.trim(),
                                lastOpened = Clock.System.now().toEpochMilliseconds()
                            )
                        } else {
                            it
                        }
                    }
                    
                    if (state.value.author.isNotBlank()) {
                        GlobalSettings.localAuthor = state.value.author.trim()
                    }
                    
                    // Load the updated workspace if we're in the workspace
                    WorkspaceRepository.loadWorkspace(updatedWorkspace)
                    
                    triggerEffect(ProjectCreationDialogContract.Effect.OpenWorkspace)
                } else {
                    // Create mode: Create new project
                    val workspace = SavableWorkspaceData(
                        title = state.value.name.trim(),
                        author = state.value.author.trim().ifBlank { "Unknown Author" },
                        launchpadDevices = listOf(
                            SavableWorkspaceData.SavableViewportLaunchpad(
                                positionX = 0f,
                                positionY = 0f,
                                type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                            )
                        )
                    )

                    if (state.value.author.isNotBlank()) {
                        GlobalSettings.localAuthor = state.value.author.trim()
                    }

                    WorkspaceRepository.loadWorkspace(workspace)

                    triggerEffect(ProjectCreationDialogContract.Effect.OpenWorkspace)
                }
            }

            is ProjectCreationDialogContract.Event.OnChangeName -> {
                updateState(
                    state.value.copy(
                        name = event.value,
                        isNameValid = event.value.isNotBlank()
                    )
                )
            }

            is ProjectCreationDialogContract.Event.OnChangeAuthor -> {
                updateState(state.value.copy(author = event.value))
            }
        }
    }
}

sealed interface ProjectCreationDialogContract {
    sealed interface Event {
        data object OnClickCreateProject : Event

        data class OnChangeName(
            val value: String
        ): Event

        data class OnChangeAuthor(
            val value: String
        ): Event
    }

    sealed interface Effect {
        data object OpenWorkspace : Effect
    }

    data class State(
        val name: String,
        val author: String,
        val isNameValid: Boolean = false,
        val projectPath: String? = null,
    )
}