package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem
import dev.anthonyhfm.amethyst.core.util.getDeviceCapabilities

@Composable
fun GeneralSettingsView() {
    val caps = remember { getDeviceCapabilities() }

    var selectedFPS by remember { mutableStateOf(GlobalSettings.performanceFPS) }
    var selectedGradientSmoothness by remember { mutableStateOf(GlobalSettings.gradientSmoothness) }
    var animationsEnabled by remember { mutableStateOf(GlobalSettings.enableAnimations) }

    SettingsCategory(
        title = "General",
    ) {
        SettingsItem(
            title = "Frames per Second (FPS)",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val fpsChoices = if (caps.showFPSSettings) listOf(60, 90, 120) else listOf(caps.initialFPS)

                fpsChoices.forEach { fps ->
                    if (selectedFPS == fps) {
                        FilledTonalButton(
                            onClick = {
                                selectedFPS = fps
                                GlobalSettings.performanceFPS = fps
                            }
                        ) {
                            Text(text = fps.toString())
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedFPS = fps
                                GlobalSettings.performanceFPS = fps
                            }
                        ) {
                            Text(text = fps.toString())
                        }
                    }
                }
            }
        }

        SettingsItem(
            title = "Gradient Smoothness",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    0.5f to "50%",
                    0.75f to "75%",
                    1f to "100%"
                ).forEach { (value, label) ->
                    if (selectedGradientSmoothness == value) {
                        FilledTonalButton(
                            onClick = {
                                selectedGradientSmoothness = value
                                GlobalSettings.gradientSmoothness = value
                            }
                        ) {
                            Text(text = label)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedGradientSmoothness = value
                                GlobalSettings.gradientSmoothness = value
                            }
                        ) {
                            Text(text = label)
                        }
                    }
                }
            }
        }

        SettingsItem(
            title = "Enable Animations",
        ) {
            Switch(
                checked = animationsEnabled,
                onCheckedChange = {
                    animationsEnabled = it

                    GlobalSettings.enableAnimations = it
                }
            )
        }
    }
}