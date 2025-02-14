package dev.anthonyhfm.amethyst.core.data

import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.core.data.tracks.Track
import kotlinx.coroutines.flow.MutableStateFlow

class ProjectRepository {
    val launchpadConfigs: MutableStateFlow<List<ProjectDeviceConfig>> =
        MutableStateFlow(
            listOf(
                ProjectDeviceConfig(
                    name = "Launchpad 1",
                )
            )
        )

    val tracks: MutableStateFlow<List<Track>> =
        MutableStateFlow(
            listOf(
                EffectTrack(
                    name = "Example",
                    projectDeviceIndex = 0
                )
            )
        )
}