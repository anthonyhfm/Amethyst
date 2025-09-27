package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem

@Composable
fun AudioSettingsView() {
    var volume: Float by remember { mutableStateOf(1f) }

    SettingsCategory(
        title = "Audio",
    ) {
        SettingsItem("Master Volume") {
            Slider(
                value = volume,
                onValueChange = {
                    volume = it
                },
                onValueChangeFinished = {

                },
                modifier = Modifier
                    .width(200.dp)
                    .height(28.dp)
            )
        }
    }
}