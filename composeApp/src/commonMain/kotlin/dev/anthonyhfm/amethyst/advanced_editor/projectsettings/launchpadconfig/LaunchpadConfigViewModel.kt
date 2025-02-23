package dev.anthonyhfm.amethyst.advanced_editor.projectsettings.launchpadconfig

import androidx.lifecycle.ViewModel
import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import kotlinx.coroutines.flow.update

class LaunchpadConfigViewModel(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    fun createDevice() {
        projectRepository.launchpadConfigs.update {
            it.plus(
                ProjectDeviceConfig()
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

        projectRepository.launchpadConfigs.update {
            it.map {
                if (it == before) {
                    after
                } else {
                    it
                }
            }
        }
    }
}