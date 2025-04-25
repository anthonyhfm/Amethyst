package dev.anthonyhfm.amethyst.workspace.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceSettings(
    val bpm: Int
)
