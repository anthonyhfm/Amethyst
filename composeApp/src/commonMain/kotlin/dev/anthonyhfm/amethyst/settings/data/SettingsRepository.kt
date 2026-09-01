package dev.anthonyhfm.amethyst.settings.data

import com.russhwolf.settings.Settings
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyLead

object SettingsRepository {
    val platformSettings = Settings()

    private val allSettingsGroups: MutableList<SettingsGroup> = mutableListOf()

    val settingsGroups: List<SettingsGroup>
        get() = allSettingsGroups.filter { it.settings.isNotEmpty() }

    init {
        allSettingsGroups.add(GeneralSettings)
        allSettingsGroups.add(AudioSettings)

        if (platform is Platform.Desktop) {
            allSettingsGroups.add(DiscordSettings)
        }

        allSettingsGroups.add(ExperimentalSettings)
    }
}