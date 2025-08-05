package dev.anthonyhfm.amethyst.core.data.settings

import com.russhwolf.settings.Settings
import dev.anthonyhfm.amethyst.core.heaven.Heaven
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import kotlinx.serialization.json.Json

object GlobalSettings {
    private val settings: Settings = Settings()

    var recentWorkspaces: List<RecentWorkspace>
        get() {
            val jsonString = settings.getString("recentWorkspaces", "[]")

            return Json.decodeFromString<List<RecentWorkspace>>(jsonString)
        }
        set(value) {
            settings.putString("recentWorkspaces", Json.encodeToString(value))
        }

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

    var enableDiscordRPC: Boolean
        get() = settings.getBoolean("enableDiscordRPC", true)
        set(value) {
            settings.putBoolean("enableDiscordRPC", value)
        }
}