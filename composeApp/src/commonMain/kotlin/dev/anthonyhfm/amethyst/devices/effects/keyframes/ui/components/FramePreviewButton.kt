package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract

@Composable
fun FramePreviewButton(
    index: Int,
    timing: Timing,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(4.dp),

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = when (timing) {
                is Timing.Duration -> {
                    "${timing.duration.inWholeMilliseconds} ms"
                }

                is Timing.Rythm -> {
                    timing.timing.text
                }
            },
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .width(56.dp)
                .background(MaterialTheme.colorScheme.tertiary)
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiary
        )

        Text(
            text = "Frame ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}