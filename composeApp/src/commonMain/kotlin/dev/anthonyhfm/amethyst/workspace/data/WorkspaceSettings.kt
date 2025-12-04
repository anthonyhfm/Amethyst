package dev.anthonyhfm.amethyst.workspace.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceSettings(
    val bpm: Double = 120.0,
    val autoPlayShowButtonPresses: Boolean = true,
    val autoPlayShowLights: Boolean = true
)
