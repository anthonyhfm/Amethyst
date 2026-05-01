package dev.anthonyhfm.amethyst.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.settings.data.Setting

@Composable
internal fun <T> SelectSettingContent(setting: Setting.Select<T>) {
    var expanded by remember { mutableStateOf(false) }
    val current by setting.flow.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = setting.title,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(
                vertical = 8.dp,
                horizontal = 4.dp,
            ),
        ) {
            Text(
                text = setting.label(current),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )

            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                setting.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(setting.label(option)) },
                        onClick = {
                            setting.update(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}