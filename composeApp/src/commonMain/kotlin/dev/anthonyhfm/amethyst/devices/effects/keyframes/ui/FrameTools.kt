package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesUIState

@Composable
fun FrameTools(
    modifier: Modifier = Modifier,
    state: KeyframesUIState
) {
    Card(
        modifier = modifier
            .padding(bottom = 24.dp)
            .padding(horizontal = 12.dp)
            .width(280.dp)
            .fillMaxHeight()
    ) {

    }
}