package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem

@Composable
fun GeneralSettingsView() {
    var selectedFPS by remember { mutableStateOf(GlobalSettings.performanceFPS) }
    var selectedGradientSmoothness by remember { mutableStateOf(GlobalSettings.gradientSmoothness) }

    SettingsCategory(
        title = "General",
    ) {
        SettingsItem(
            title = "Frames per Second (FPS)",
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(90, 120, 140, 180).forEach { fps ->
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
    }
}