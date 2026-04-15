package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun AudioSettingsView() {
    var volume by remember { mutableStateOf(GlobalSettings.masterVolume) }

    SettingsCategory(
        title = "Audio",
    ) {
        SettingsItem("Master Volume") {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${(volume * 100f).roundToInt()}%",
                    style = Theme[typography][small].copy(color = Theme[colors][foreground]),
                )

                Slider(
                    value = volume,
                    onValueChange = {
                        volume = it
                        GlobalSettings.masterVolume = it
                    },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}
