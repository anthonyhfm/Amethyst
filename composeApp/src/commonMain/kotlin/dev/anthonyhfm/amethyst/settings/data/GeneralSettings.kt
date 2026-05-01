package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform

object GeneralSettings : SettingsGroup("General") {
    val performanceFPS: Setting.Select<Int> = select(
        key = "framesPerSecond",
        title = "Refresh rate",
        default = if (platform is Platform.iOS || platform is Platform.Android) 90 else 120,
        options = listOf(60, 90, 120),
        codec = SettingCodec.Int,
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

    // Not in the settings list — accessed programmatically by HomeRepository
    val localAuthor: Setting.TextField = Setting.TextField(
        key = "localAuthor",
        title = "Author Name",
        default = "",
    )
}
