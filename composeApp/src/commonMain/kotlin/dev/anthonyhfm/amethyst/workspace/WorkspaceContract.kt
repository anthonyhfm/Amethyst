package dev.anthonyhfm.amethyst.workspace

interface WorkspaceContract {
    data class State(
        val mode: dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode,
    )
}
