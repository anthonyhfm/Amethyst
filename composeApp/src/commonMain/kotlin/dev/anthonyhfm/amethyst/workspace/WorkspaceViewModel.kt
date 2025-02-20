package dev.anthonyhfm.amethyst.workspace

import androidx.lifecycle.ViewModel
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class WorkspaceViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    val state = MutableStateFlow(WorkspaceContract.State())

    fun onEvent(event: WorkspaceContract.Event) {
        when (event) {
            is WorkspaceContract.Event.AddDeviceToViewport -> {
                val device = LaunchpadViewportElement()

                device.onEvent = { onEvent(it) }

                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.plus(device)
                    )
                }
            }

            is WorkspaceContract.Event.ChangeWorkspaceMode -> {
                state.update {
                    it.copy(mode = event.mode)
                }
            }

            is WorkspaceContract.Event.ChangeViewportElementPosition -> {
                state.update {
                    it.copy(
                        viewportElements = it.viewportElements.toMutableList().apply {
                            this[event.index].position.value = event.offset
                        }
                    )
                }
            }

            is WorkspaceContract.Event.OnClickDeviceConfigure -> {
                state.update {
                    it.copy(
                        showDeviceConfigurator = event.index
                    )
                }
            }

            is WorkspaceContract.Event.OnDismissDeviceConfigure -> {
                state.update {
                    it.copy(
                        showDeviceConfigurator = null
                    )
                }
            }
        }
    }

    fun triggerEffect(effect: WorkspaceContract.Effect) {

    }
}