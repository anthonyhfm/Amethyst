package dev.anthonyhfm.amethyst.core.data.settings

import com.russhwolf.settings.Settings
import dev.anthonyhfm.amethyst.core.heaven.Heaven

object GlobalSettings {
    private val settings: Settings = Settings()

    var perforanceFPS: Int
        get() = settings.getInt("framesPerSecond", 120)
        set(value) {
            settings.putInt("framesPerSecond", value)

            Heaven.fps = value
        }

    var gradientSmoothness: Float
        get() = settings.getFloat("gradientSmoothness", 1f)
        set(value) {
            settings.putFloat("framesPerSecond", value)
        }
}