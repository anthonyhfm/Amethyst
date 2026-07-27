package dev.anthonyhfm.amethyst.settings.data

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*

object ExperimentalSettings : SettingsGroup("Experimental Features", Res.string.settings_experimental_group_title) {
    val liveCollaboration: Setting.Toggle = toggle(
        key = "liveCollaboration",
        title = "Live Collaboration (LAN-only)",
        titleRes = Res.string.settings_experimental_live_collab_title,
        default = false,
    )

    val crystalCompositions: Setting.Toggle = toggle(
        key = "crystalCompositions",
        title = "Crystal Compositions",
        titleRes = Res.string.settings_experimental_crystal_compositions_title,
        default = false,
    )
}
