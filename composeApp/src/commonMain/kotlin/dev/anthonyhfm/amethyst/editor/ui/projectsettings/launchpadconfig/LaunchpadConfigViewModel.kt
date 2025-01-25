package dev.anthonyhfm.amethyst.editor.ui.projectsettings.launchpadconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import kotlinx.coroutines.launch

class LaunchpadConfigViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    fun createDevice() {
        viewModelScope.launch {
            projectRepository.launchpadConfigs.emit(
                projectRepository.launchpadConfigs.value.plus(
                    ProjectDeviceConfig(
                        name = "Launchpad ${projectRepository.launchpadConfigs.value.size + 1}"
                    )
                )
            )
        }
    }

    fun changeDeviceProperties(before: ProjectDeviceConfig, after: ProjectDeviceConfig) {
        if (before.input != after.input) {
            before.input?.close()
        }

        if (before.output != after.output) {
            before.output?.close()
        }

        viewModelScope.launch {
            projectRepository.launchpadConfigs.emit(
                projectRepository.launchpadConfigs.value.map {
                    if (it == before) {
                        after
                    } else {
                        it
                    }
                }
            )
        }
    }
}