package dev.anthonyhfm.amethyst.settings.data

data class SettingsCluster(
    val name: String,
    val children: List<Nothing> = emptyList() // TODO: Add Settings-Interface
)
