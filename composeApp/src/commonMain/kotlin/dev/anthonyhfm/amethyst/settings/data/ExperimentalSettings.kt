package dev.anthonyhfm.amethyst.settings.data

object ExperimentalSettings : SettingsGroup("Experimental Features") {
    val liveCollaboration: Setting.Toggle = toggle(
        key = "liveCollaboration",
        title = "Live Collaboration (LAN-only)",
        default = false,
    )

    val abletonTutorial: Setting.Toggle = toggle(
        key = "abletonTutorial",
        title = "Ableton Tutorial Detection",
        default = false,
    )
}
