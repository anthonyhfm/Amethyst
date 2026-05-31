package dev.anthonyhfm.amethyst.core.network.sync

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

object ViewportDeviceFactory {

    fun create(
        type: ViewportDeviceType,
        id: String,
        position: Offset
    ): LaunchpadViewportElement = when (type) {
        ViewportDeviceType.LAUNCHPAD_PRO       -> ViewportLaunchpadPro()
        ViewportDeviceType.LAUNCHPAD_PRO_MK3   -> ViewportLaunchpadProMk3()
        ViewportDeviceType.LAUNCHPAD_X         -> ViewportLaunchpadX()
        ViewportDeviceType.LAUNCHPAD_MK2       -> ViewportLaunchpadMk2()
        ViewportDeviceType.LAUNCHPAD_IDEALISED -> ViewportLaunchpadIdealised()
        ViewportDeviceType.MYSTRIX             -> ViewportMystrix()
        ViewportDeviceType.MIDIFIGHTER64       -> ViewportMidiFighter64()
    }.also { element ->
        element.launchpadId = id
        element.position.value = position
    }
}

fun LaunchpadViewportElement.toViewportDeviceType(): ViewportDeviceType = when (this) {
    is ViewportLaunchpadPro       -> ViewportDeviceType.LAUNCHPAD_PRO
    is ViewportLaunchpadProMk3    -> ViewportDeviceType.LAUNCHPAD_PRO_MK3
    is ViewportLaunchpadX         -> ViewportDeviceType.LAUNCHPAD_X
    is ViewportLaunchpadMk2       -> ViewportDeviceType.LAUNCHPAD_MK2
    is ViewportLaunchpadIdealised -> ViewportDeviceType.LAUNCHPAD_IDEALISED
    is ViewportMystrix            -> ViewportDeviceType.MYSTRIX
    is ViewportMidiFighter64      -> ViewportDeviceType.MIDIFIGHTER64
    else                          -> error("Unknown LaunchpadViewportElement subtype: ${this::class.simpleName}")
}
