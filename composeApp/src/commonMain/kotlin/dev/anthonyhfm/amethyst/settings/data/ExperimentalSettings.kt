package dev.anthonyhfm.amethyst.settings.data

object ExperimentalSettings : SettingsGroup("Experimental Features") {
    val extensions: Setting.Toggle = toggle(
        key = "experimentalExtensions",
        title = "Amethyst Gems",
        default = false,
    )
}
