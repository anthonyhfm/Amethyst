package dev.anthonyhfm.amethyst.core.data

import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import kotlinx.coroutines.flow.MutableStateFlow

class ProjectRepository {
    val launchpadConfigs: MutableStateFlow<List<ProjectDeviceConfig>> =
        MutableStateFlow(emptyList())
}