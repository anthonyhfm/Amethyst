package dev.anthonyhfm.amethyst.workspace.ui.viewport.elements

import dev.anthonyhfm.amethyst.workspace.ViewportRepository

internal fun resolveLaunchpadOrigin(
    origin: Any?,
    x: Int,
    y: Int,
    launchpadId: String? = null,
): LaunchpadViewportElement? {
    val devices = ViewportRepository.devices.value
    fun LaunchpadViewportElement.containsCoordinate(): Boolean {
        val startX = position.value.x.toInt()
        val startY = position.value.y.toInt()
        return x in startX until (startX + layout.cols) &&
            y in startY until (startY + layout.rows)
    }

    return launchpadId?.let { id -> devices.firstOrNull { it.launchpadId == id } }
        ?: (origin as? LaunchpadViewportElement)?.takeIf { it.containsCoordinate() }
        ?: devices.firstOrNull { it.containsCoordinate() }
        ?: devices.singleOrNull()
}
