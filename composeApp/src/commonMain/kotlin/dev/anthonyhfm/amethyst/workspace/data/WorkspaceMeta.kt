package dev.anthonyhfm.amethyst.workspace.data

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceMeta(
    var path: String? = null,
    var title: String = "Untitled",
    var author: String = "Unknown Author",
    var settings: WorkspaceSettings = WorkspaceSettings(),
    var autoPlay: AutoPlayData = AutoPlayData(emptyMap())
)
