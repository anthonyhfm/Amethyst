package dev.anthonyhfm.amethyst.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.settings.data.Setting

@Composable
fun SettingsRenderer(isFirstIndex: Boolean, isLastIndex: Boolean, setting: Setting<*>) {
    Box(
        modifier = Modifier
            .clip(
                MaterialTheme.shapes.large.copy(
                    topStart = if (isFirstIndex) MaterialTheme.shapes.large.topStart else MaterialTheme.shapes.small.topStart,
                    topEnd = if (isFirstIndex) MaterialTheme.shapes.large.topEnd else MaterialTheme.shapes.small.topEnd,
                    bottomStart = if (isLastIndex) MaterialTheme.shapes.large.bottomStart else MaterialTheme.shapes.small.bottomStart,
                    bottomEnd = if (isLastIndex) MaterialTheme.shapes.large.bottomEnd else MaterialTheme.shapes.small.bottomEnd,
                )
            )
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        when (setting) {
            is Setting.Select<*> -> SelectSettingContent(setting)
            is Setting.Slider -> SliderSettingContent(setting)
            is Setting.TextField -> { }
            is Setting.Toggle -> ToggleSettingContent(setting)
        }
    }
}