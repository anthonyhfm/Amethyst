package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class SavableWorkspaceData(
    val version: Version = amethystVersion,
    val title: String = "Untitled Workspace",
    val author: String = "Unknown Author",
    val settings: WorkspaceSettings = WorkspaceSettings(),
    val timelineData: List<@Polymorphic TimelineTrack<*>> = emptyList(),
    val lights: StateChain = StateChain(),
    val sampling: StateChain = StateChain(),
    val autoPlay: AutoPlayData = AutoPlayData(emptyMap()),
    val launchpadDevices: List<SavableViewportLaunchpad> = emptyList(),
    val macros: List<Macro> = listOf(Macro(0)),

    @Transient
    var path: String? = null, // This is not serialized, used for file operations
) {
    @Serializable
    data class SavableViewportLaunchpad(
        val positionX: Float,
        val positionY: Float,
        val type: ViewportDeviceType
    ) {
        enum class ViewportDeviceType {
            LAUNCHPAD_PRO,
            LAUNCHPAD_PRO_MK3,
            LAUNCHPAD_X,
            LAUNCHPAD_MK2,
            MYSTRIX,
            MIDIFIGHTER64
        }
    }
}

@Serializable
data class RecentWorkspace @OptIn(ExperimentalTime::class) constructor(
    val title: String,
    val path: String,
    val lastOpened: Long = Clock.System.now().toEpochMilliseconds()
)