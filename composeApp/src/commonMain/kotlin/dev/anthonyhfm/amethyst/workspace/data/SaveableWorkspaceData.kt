package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SaveableWorkspaceData(
    val title: String = "Untitled Workspace",
    val settings: WorkspaceSettings = WorkspaceSettings(),
    val lights: StateChain = StateChain(),
    val sampling: StateChain = StateChain(),
    @Transient
    var path: String? = null, // This is not serialized, used for file operations
)

@Serializable
data class RecentWorkspace(
    val title: String,
    val path: String,
)