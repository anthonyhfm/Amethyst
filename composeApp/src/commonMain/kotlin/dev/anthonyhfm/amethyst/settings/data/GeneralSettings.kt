package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform

object GeneralSettings : SettingsGroup("General") {
    val performanceFPS: Setting.Select<Int> = select(
        key = "framesPerSecond",
        title = "Refresh rate",
        default = if (platform is Platform.iOS || platform is Platform.Android) 90 else 120,
        options = listOf(60, 90, 120, 180, 240),
        codec = SettingCodec.Int,
        label = { "$it Hz" },
        onUpdate = { Heaven.fps = it },
    )

    val gradientSmoothness: Setting.Select<Float> = select(
        key = "gradientSmoothness",
        title = "Gradient Smoothness",
        default = 1f,
        options = listOf(0.5f, 0.75f, 1f),
        codec = SettingCodec.Float,
        label = { "${(it * 100).toInt()}%" },
    )

    val simplifiedGraphics: Setting.Toggle = toggle(
        key = "simplifiedGraphics",
        title = "Simplified Graphics",
        default = false,
    )

    val reducedMotion: Setting.Toggle = toggle(
        key = "reducedMotion",
        title = "Reduced Motion",
        default = false,
    )

    val hoverTime: Setting.Select<Int> = select(
        key = "hoverTime",
        title = "Hover Time",
        default = 100,
        options = listOf(0, 100, 250, 500),
        codec = SettingCodec.Int,
        label = { "${it.toInt()} ms" },
    )

    // Not in the settings list — accessed programmatically by HomeRepository
    val localAuthor: Setting.TextField = Setting.TextField(
        key = "localAuthor",
        title = "Author Name",
        default = "",
    )
}
