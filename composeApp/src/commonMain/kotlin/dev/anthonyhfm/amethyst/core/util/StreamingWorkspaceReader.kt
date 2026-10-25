package dev.anthonyhfm.amethyst.core.util

import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile

internal expect fun readStreamingAmethystWorkspace(file: PlatformFile): SavableWorkspaceData?
