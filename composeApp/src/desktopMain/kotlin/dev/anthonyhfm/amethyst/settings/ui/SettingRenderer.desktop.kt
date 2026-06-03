package dev.anthonyhfm.amethyst.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.settings.data.Setting
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.components.primitives.Switch
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun SettingRenderer(setting: Setting<*>) {
    @Suppress("UNCHECKED_CAST")
    when (setting) {
        is Setting.Toggle -> ToggleSettingItem(setting)
        is Setting.Select<*> -> SelectSettingItem(setting as Setting.Select<Any>)
        is Setting.Slider -> SliderSettingItem(setting)
        is Setting.TextField -> TextFieldSettingItem(setting)
    }
}

@Composable
private fun ToggleSettingItem(setting: Setting.Toggle) {
    val checked by setting.flow.collectAsState()
    SettingsItem(title = setting.title) {
        Switch(
            checked = checked,
            onCheckedChange = { setting.update(it) },
        )
    }
}

@Composable
private fun <T : Any> SelectSettingItem(setting: Setting.Select<T>) {
    val selected by setting.flow.collectAsState()
    SettingsItem(title = setting.title) {
        Select(
            value = setting.label(selected),
            onValueChange = { label ->
                setting.options.firstOrNull { setting.label(it) == label }
                    ?.let { setting.update(it) }
            },
            options = setting.options.map { setting.label(it) },
            modifier = Modifier.width(160.dp),
        )
    }
}

@Composable
private fun SliderSettingItem(setting: Setting.Slider) {
    val value by setting.flow.collectAsState()
    SettingsItem(title = setting.title) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.width(220.dp)) {
                Slider(
                    value = value,
                    valueRange = setting.range,
                    onValueChange = { setting.update(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "${(value * 100f).roundToInt()}%",
                    modifier = Modifier.fillMaxWidth(),
                    style = Theme[typography][small].copy(
                        color = Theme[colors][foreground],
                        textAlign = TextAlign.End,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TextFieldSettingItem(setting: Setting.TextField) {
    val value by setting.flow.collectAsState()
    SettingsItem(title = setting.title) {
        Text(
            text = value,
            style = Theme[typography][small].copy(color = Theme[colors][foreground]),
        )
    }
}


