package dev.anthonyhfm.amethyst.home.ui.views

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData

class ProjectCreationDialogViewModel : BaseViewModel<ProjectCreationDialogContract.State, ProjectCreationDialogContract.Event, ProjectCreationDialogContract.Effect>(
    initialState = ProjectCreationDialogContract.State(
        name = "",
        author = GlobalSettings.localAuthor
    )
) {

    override fun onEvent(event: ProjectCreationDialogContract.Event) {
        when (event) {
            is ProjectCreationDialogContract.Event.OnClickCreateProject -> {
                if (!state.value.isNameValid) return

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
    )
}