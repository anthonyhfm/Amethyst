package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem

@Composable
fun ExperimentalSettingsView() {
    var abletonPush2Support by remember { mutableStateOf(GlobalSettings.experimentalAbletonPush2Support) }
    var apolloConversionSupport by remember { mutableStateOf(GlobalSettings.experimentalApolloConversionSupport) }

    SettingsCategory(
        title = "Experiments",
    ) {
        SettingsItem(
            title = "Experimental Ableton Push 2 Support",
        ) {
            Switch(
                checked = abletonPush2Support,
                onCheckedChange = {
                    abletonPush2Support = it
                    GlobalSettings.experimentalAbletonPush2Support = it
                }
            )
        }

        SettingsItem(
            title = "Experimental Apollo Conversion Support",
        ) {
            Switch(
                checked = apolloConversionSupport,
                onCheckedChange = {
                    apolloConversionSupport = it
                    GlobalSettings.experimentalApolloConversionSupport = it
                }
            )
        }
    }
}