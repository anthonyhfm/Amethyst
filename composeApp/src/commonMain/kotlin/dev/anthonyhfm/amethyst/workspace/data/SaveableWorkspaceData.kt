package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.audio.AudioClip
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SaveableWorkspaceData(
    val title: String = "Untitled Workspace",
    val settings: WorkspaceSettings = WorkspaceSettings(),
    val lights: StateChain = StateChain(),
    val sampling: StateChain = StateChain(),
    val launchpadDevices: List<SavableViewportLaunchpad> = emptyList(),
    val macros: List<Macro> = listOf(Macro(0)),
    val audioClips: List<AudioClip> = emptyList(),
    val author: String = "Unknown",
    val version: String = "1.0.0",
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
data class RecentWorkspace(
    val title: String,
    val path: String,
)