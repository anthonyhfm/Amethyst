package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.serialization.Serializable

@Serializable
internal data class ViewportDeviceModelChange(
    val type: ViewportDeviceType,
    val style: String? = null,
) {
    companion object {
        fun from(element: LaunchpadViewportElement): ViewportDeviceModelChange = ViewportDeviceModelChange(
            type = element.toViewportDeviceType(),
            style = (element as? ViewportMidiFighter64)?.style?.name,
        )
    }
}
