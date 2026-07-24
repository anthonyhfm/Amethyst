package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*

object GeneralSettings : SettingsGroup("General", Res.string.settings_general_group_title) {
    val language: Setting.Select<LanguageOption> = select(
        key = "language",
        title = "Language",
        titleRes = null,
        default = LanguageOptions.English,
        options = LanguageOptions.all,
        codec = LanguageOptionCodec,
        label = { it.displayName },
    )

    val performanceFPS: Setting.Select<Int> = select(
        key = "framesPerSecond",
        title = "Refresh rate",
        titleRes = Res.string.settings_general_refresh_rate_title,
        default = if (platform is Platform.iOS || platform is Platform.Android) 90 else 120,
        options = listOf(60, 90, 120, 180, 240),
        codec = SettingCodec.Int,
        label = { "$it Hz" },
        onUpdate = { Heaven.fps = it },
    )

    val gradientSmoothness: Setting.Select<Float> = select(
        key = "gradientSmoothness",
        title = "Gradient Smoothness",
        titleRes = Res.string.settings_general_gradient_smoothness_title,
        default = 1f,
        options = listOf(0.5f, 0.75f, 1f),
        codec = SettingCodec.Float,
        label = { "${(it * 100).toInt()}%" },
    )

    val simplifiedGraphics: Setting.Toggle = toggle(
        key = "simplifiedGraphics",
        title = "Simplified Graphics",
        titleRes = Res.string.settings_general_simplified_graphics_title,
        default = false,
    )

    val reducedMotion: Setting.Toggle = toggle(
        key = "reducedMotion",
        title = "Reduced Motion",
        titleRes = Res.string.settings_general_reduced_motion_title,
        default = false,
    )

    val alwaysShowGrid: Setting.Toggle = toggle(
        key = "alwaysShowGrid",
        title = "Always show grid",
        titleRes = Res.string.settings_general_always_show_grid_title,
        default = false,
    )

    val hoverTime: Setting.Select<Int> = select(
        key = "hoverTime",
        title = "Hover Time",
        titleRes = Res.string.settings_general_hover_time_title,
        default = 100,
        options = listOf(0, 100, 250, 500),
        codec = SettingCodec.Int,
        label = { "${it.toInt()} ms" },
    )

    // Not in the settings list — accessed programmatically by HomeRepository
    val localAuthor: Setting.TextField = Setting.TextField(
        key = "localAuthor",
        title = "Author Name",
        titleRes = Res.string.settings_general_author_name_title,
        default = "",
    )
}
