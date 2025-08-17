package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract

@Composable
fun FramePreviewButton(
    index: Int,
    selected: Boolean,
    timing: Timing,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit,
    parent: dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice? = null
) {
    val selections by SelectionManager.selections.collectAsState()
    val isSelectedInManager = parent?.let { parentDevice ->
        selections.any {
            it is Selectable.KeyframeItem &&
                    it.parent == parentDevice &&
                    it.frameIndex == index
        }
    } ?: false

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .fillMaxWidth()
            .background(
                when {
                    selected && isSelectedInManager -> MaterialTheme.colorScheme.tertiary
                    isSelectedInManager -> MaterialTheme.colorScheme.tertiary.copy(0.5f)
                    selected -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
            )
            .clickable {
                onEvent(
                    KeyframesChainDeviceContract.Event.OnSelectFrame(
                        frameIndex = index,
                        rangeSelect = ModifierKeysState.isShiftPressed,
                        multiSelect = ModifierKeysState.isCtrlPressed
                    )
                )
            }
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
                .background(
                    when {
                        selected && isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                        isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                        selected -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onTertiary
                    }
                )
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            fontWeight = FontWeight.Bold,
            color = when {
                selected && isSelectedInManager -> MaterialTheme.colorScheme.tertiary
                isSelectedInManager -> MaterialTheme.colorScheme.tertiary
                selected -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.tertiary
            },
        )

        Text(
            text = "Frame ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            color = when {
                selected && isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                selected -> MaterialTheme.colorScheme.onTertiary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
