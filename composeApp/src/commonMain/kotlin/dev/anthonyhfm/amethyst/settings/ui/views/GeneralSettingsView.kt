package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem

@Composable
fun GeneralSettingsView() {
    SettingsCategory(
        title = "General",
    ) {
        SettingsItem(
            title = "Updates",
        ) {
            Button(
                onClick = {

                }
            ) {
                Text("Check for updates")
            }
        }

        SettingsItem(
            title = "Language",
        ) {
            Text(
                text = "Currently not supported",
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        SettingsItem(
            title = "Dark Mode",
        ) {
            Switch(
                checked = true,
                onCheckedChange = {

                }
            )
        }
    }
}