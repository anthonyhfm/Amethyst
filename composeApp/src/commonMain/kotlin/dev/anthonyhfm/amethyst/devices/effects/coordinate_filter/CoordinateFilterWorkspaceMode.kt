package dev.anthonyhfm.amethyst.devices.effects.coordinate_filter

import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

class CoordinateFilterWorkspaceMode : WorkspaceContract.WorkspaceMode {
    override val displayName: String = "Coordinate-Filter Picker"
    override val selectable: Boolean = false
}