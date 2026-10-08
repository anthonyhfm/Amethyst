package dev.anthonyhfm.amethyst.timeline.ui.components.track

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.issue_071_track_volume
import amethyst.composeapp.generated.resources.issue_071_track_volume_reset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Volume2
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.ui.components.primitives.Slider
import dev.anthonyhfm.amethyst.ui.components.primitives.Tooltip
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TrackVolumeControl(
    volume: Float,
    contentColor: Color,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = TimelineTrackAutomationTarget.VOLUME
    val volumeLabel = stringResource(resource = Res.string.issue_071_track_volume)
    val resetLabel = stringResource(resource = Res.string.issue_071_track_volume_reset)
    val valueLabel = target.formatValue(value = volume)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tooltip(
            text = volumeLabel,
            anchor = {
                Icon(
                    imageVector = Lucide.Volume2,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(size = 16.dp),
                )
            },
        )

        Slider(
            value = target.valueToDisplayProgress(value = volume),
            onValueChange = { progress ->
                val displayValue = target.snapDisplayValue(
                    displayValue = target.displayProgressToDisplayValue(progress = progress)
                )
                onVolumeChange(target.displayValueToValue(displayValue = displayValue))
            },
            modifier = Modifier
                .weight(weight = 1f)
                .semantics {
                    contentDescription = volumeLabel
                    stateDescription = valueLabel
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    val displayValue = when (event.key) {
                        Key.DirectionLeft, Key.DirectionDown -> target.valueToDisplayValue(value = volume) - 1f
                        Key.DirectionRight, Key.DirectionUp -> target.valueToDisplayValue(value = volume) + 1f
                        Key.MoveHome -> target.displayMinimumValue()
                        Key.MoveEnd -> target.displayMaximumValue()
                        else -> return@onPreviewKeyEvent false
                    }
                    onVolumeChange(target.displayValueToValue(displayValue = displayValue))
                    true
                },
        )

        Tooltip(
            text = resetLabel,
            anchor = {
                Text(
                    text = valueLabel,
                    color = contentColor,
                    style = Theme[typography][small],
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .width(width = 48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = resetLabel,
                            onClick = {
                                onVolumeChange(target.defaultValue)
                            }
                        )
                        .padding(vertical = 4.dp),
                )
            }
        )
    }
}
