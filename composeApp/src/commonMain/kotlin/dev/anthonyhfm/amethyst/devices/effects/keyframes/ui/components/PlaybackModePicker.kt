package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.PlaybackMode
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun PlaybackModePicker(
    selectedMode: PlaybackMode,
    onModeSelected: (PlaybackMode) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(DefaultShape)
            .width(220.dp)
            .background(Theme[colors][card])
            .border(1.dp, Theme[colors][border], DefaultShape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Playback Mode",
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )

        Tabs(
            selectedTab = selectedMode.name,
            tabs = PlaybackMode.entries.map { it.name },
            modifier = Modifier.fillMaxWidth(),
        ) {
            TabsList(modifier = Modifier.fillMaxWidth()) {
                PlaybackMode.entries.forEach { mode ->
                    TabsTrigger(
                        key = mode.name,
                        selected = selectedMode == mode,
                        onSelected = { onModeSelected(mode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = mode.name,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
