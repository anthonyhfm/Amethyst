package dev.anthonyhfm.amethyst.workspace.data

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceMeta(
    var path: String? = null,
    var title: String = "Untitled",
    var author: String = "Unknown Author",
    var settings: WorkspaceSettings = WorkspaceSettings(),
    var autoPlay: AutoPlayData = AutoPlayData(emptyMap())
)
