package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.ColorControls

@Composable
fun BoxScope.FrameDrawingPanel(
    state: KeyframesChainDeviceContract.KeyframesChainDeviceState,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .fillMaxHeight()
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        ColorControls(
            color = state.selectedColor.let { Color(it.first, it.second, it.third) },
            onColorChange = {
                onEvent(
                    KeyframesChainDeviceContract.Event.OnColorUpdate(it)
                )
            }
        )
    }
}