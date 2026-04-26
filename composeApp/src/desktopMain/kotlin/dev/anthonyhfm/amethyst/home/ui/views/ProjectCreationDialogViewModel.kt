package dev.anthonyhfm.amethyst.home.ui.views

import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.util.BaseViewModel
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ProjectCreationDialogViewModel(
    private val projectPath: String? = null
) : BaseViewModel<ProjectCreationDialogContract.State, ProjectCreationDialogContract.Event, ProjectCreationDialogContract.Effect>(
    initialState = if (projectPath != null) {
        // Load existing project data for editing
        loadProjectData(projectPath)
    } else {
        ProjectCreationDialogContract.State(
            name = "",
            author = HomeRepository.localAuthor(),
            projectPath = null
        )
    }
) {
    companion object {
        private fun defaultState(): ProjectCreationDialogContract.State {
            return ProjectCreationDialogContract.State(
                name = "",
                author = HomeRepository.localAuthor(),
                projectPath = null
            )
        }

        private fun loadProjectData(path: String): ProjectCreationDialogContract.State {
            val project = runBlocking { HomeRepository.loadProjectDetails(path) }

            return if (project != null) {
                ProjectCreationDialogContract.State(
                    name = project.name,
                    author = project.author,
                    isNameValid = project.name.isNotBlank(),
                    projectPath = project.projectPath,
                )
            } else {
                defaultState()
            }
        }
    }

    override fun onEvent(event: ProjectCreationDialogContract.Event) {
        when (event) {
            is ProjectCreationDialogContract.Event.OnClickCreateProject -> {
                if (!state.value.isNameValid) return

                val currentState = state.value
                viewModelScope.launch {
                    if (currentState.projectPath != null) {
                        HomeRepository.updateProject(
                            path = currentState.projectPath,
                            name = currentState.name,
                            author = currentState.author,
                        )
                    } else {
                        HomeRepository.createProject(
                            name = currentState.name,
                            author = currentState.author,
                        )
                    }

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
