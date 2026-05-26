package dev.anthonyhfm.amethyst.settings.data

object ExperimentalSettings : SettingsGroup("Experimental Features") {
    val liveCollaboration: Setting.Toggle = toggle(
        key = "liveCollaboration",
        title = "Live Collaboration (LAN-Only)",
        default = false,
    )
}
