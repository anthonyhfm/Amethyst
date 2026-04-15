package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.util.getDeviceCapabilities
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger

@Composable
fun GeneralSettingsView() {
    val caps = remember { getDeviceCapabilities() }

    var selectedFPS by remember { mutableStateOf(GlobalSettings.performanceFPS) }
    var selectedGradientSmoothness by remember { mutableStateOf(GlobalSettings.gradientSmoothness) }

    SettingsCategory(
        title = "General",
    ) {
        SettingsItem(
            title = "Frames per Second (FPS)",
        ) {
            val fpsChoices = if (caps.showFPSSettings) listOf(60, 90, 120) else listOf(caps.initialFPS)
            Tabs(
                selectedTab = selectedFPS.toString(),
                tabs = fpsChoices.map(Int::toString),
            ) {
                TabsList {
                    fpsChoices.forEach { fps ->
                        TabsTrigger(
                            key = fps.toString(),
                            selected = selectedFPS == fps,
                            onSelected = {
                                selectedFPS = fps
                                GlobalSettings.performanceFPS = fps
                            },
                        ) {
                            Text(fps.toString())
                        }
                    }
                }
            }
        }

        SettingsItem(
            title = "Gradient Smoothness",
        ) {
            val gradientChoices = listOf(
                0.5f to "50%",
                0.75f to "75%",
                1f to "100%",
            )
            Tabs(
                selectedTab = selectedGradientSmoothness.toString(),
                tabs = gradientChoices.map { it.first.toString() },
            ) {
                TabsList {
                    gradientChoices.forEach { (value, label) ->
                        TabsTrigger(
                            key = value.toString(),
                            selected = selectedGradientSmoothness == value,
                            onSelected = {
                                selectedGradientSmoothness = value
                                GlobalSettings.gradientSmoothness = value
                            },
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}
