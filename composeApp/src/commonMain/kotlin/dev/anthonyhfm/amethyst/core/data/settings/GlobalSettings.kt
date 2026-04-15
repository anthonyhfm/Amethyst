package dev.anthonyhfm.amethyst.core.data.settings

import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object GlobalSettings {
    private val settings: Settings = Settings()

    var recentWorkspaces: List<RecentWorkspace>
        get() {
            val jsonString = settings.getString("recentWorkspaces", "[]")

            return Json.decodeFromString<List<RecentWorkspace>>(jsonString).distinctBy { it.path }
        }
        set(value) {
            settings.putString("recentWorkspaces", Json.encodeToString(value.distinctBy { it.path }))
        }

    var performanceFPS: Int
        get() = settings.getInt(
            key = "framesPerSecond",
            defaultValue = if (platform is Platform.iOS || platform is Platform.Android) {
                90
            } else {
                120
            }
        )
        set(value) {
            settings.putInt("framesPerSecond", value)

            Heaven.fps = value
        }

    var gradientSmoothness: Float
        get() = settings.getFloat("gradientSmoothness", 1f)
        set(value) {
            settings.putFloat("gradientSmoothness", value)
        }

    var masterVolume: Float
        get() = settings.getFloat("masterVolume", 1f)
        set(value) {
            settings.putFloat("masterVolume", value.coerceIn(0f, 1f))
        }

    var enableDiscordRPC: Boolean
        get() = settings.getBoolean("enableDiscordRPC", true)
        set(value) {
            settings.putBoolean("enableDiscordRPC", value)
        }

    var showCurrentProject: Boolean
        get() = settings.getBoolean("showCurrentProject", true)
        set(value) {
            settings.putBoolean("showCurrentProject", value)
        }

    var showCurrentWorkspaceState: Boolean
        get() = settings.getBoolean("showCurrentWorkspaceState", true)
        set(value) {
            settings.putBoolean("showCurrentWorkspaceState", value)
        }

    var experimentalAbletonPush2Support: Boolean
        get() = settings.getBoolean("experimentalAbletonPush2Support", false)
        set(value) {
            settings.putBoolean("experimentalAbletonPush2Support", value)
        }

    var experimentalApolloConversionSupport: Boolean
        get() = settings.getBoolean("experimentalApolloConversionSupport", false)
        set(value) {
            settings.putBoolean("experimentalApolloConversionSupport", value)
        }

    var experimentalExtensions: Boolean
        get() = settings.getBoolean("experimentalExtensions", false)
        set(value) {
            settings.putBoolean("experimentalExtensions", value)
        }

    // Persisted recent colors using a serializable RGB data class
    var recentColors: List<RecentColorRGB>
        get() {
            val jsonString = settings.getString("recentColors", "[]")
            return Json.decodeFromString(ListSerializer(RecentColorRGB.serializer()), jsonString)
        }
        set(value) {
            settings.putString("recentColors", Json.encodeToString(ListSerializer(RecentColorRGB.serializer()), value))
        }

    var localAuthor: String
        get() = settings.get("localAuthor", "")
        set(value) {
            settings.putString("localAuthor", value)
        }

}
