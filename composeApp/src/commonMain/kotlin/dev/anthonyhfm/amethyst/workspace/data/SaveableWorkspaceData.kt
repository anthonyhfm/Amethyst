package dev.anthonyhfm.amethyst.workspace.data

import kotlinx.serialization.Serializable

@Serializable
data class SaveableWorkspaceData(
    val title: String,
    val author: String,
    val settings: WorkspaceSettings = WorkspaceSettings(120)
)