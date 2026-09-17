package dev.anthonyhfm.amethyst.settings.data

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import dev.anthonyhfm.amethyst.core.util.isDevMode

object ExperimentalSettings : SettingsGroup("Experimental Features", Res.string.settings_experimental_group_title) {
    val liveCollaboration: Setting.Toggle = toggle(
        key = "liveCollaboration",
        title = "Live Collaboration (LAN-only)",
        titleRes = Res.string.settings_experimental_live_collab_title,
        default = false,
    )

    val timelineChainEffects: Setting.Toggle = toggle(
        key = "timelineChainEffects",
        title = "Timeline Chain Clips",
        default = false,
    )

    val showPerformanceOverlay: Setting.Toggle = toggle(
        key = "showPerformanceOverlay",
        title = "Performance Overlay",
        titleRes = Res.string.settings_experimental_performance_overlay_title,
        default = false,
        visibleQuery = { isDevMode },
    )
}
