package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.workspace_autoplay_position
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AutoPlayTimeline(
    progress: Float,
    totalDuration: Double,
    enabled: Boolean,
    compact: Boolean = false,
    onSeek: (Float) -> Unit,
) {
    var displayedProgress by remember { mutableStateOf(progress.coerceIn(0f, 1f)) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(progress, isScrubbing) {
        if (!isScrubbing) displayedProgress = progress.coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatAutoPlayTime(displayedProgress * totalDuration),
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
            Text(
                text = formatAutoPlayTime(totalDuration),
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }

        AutoPlayScrubber(
            value = displayedProgress,
            enabled = enabled,
            onScrub = {
                isScrubbing = true
                displayedProgress = it
            },
            onScrubFinished = {
                displayedProgress = it
                isScrubbing = false
                onSeek(it)
            },
        )
    }
}

@Composable
private fun AutoPlayScrubber(
    value: Float,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
) {
    val background = Theme[colors][secondary]
    val foreground = Theme[colors][primary]
    val safeValue = value.coerceIn(0f, 1f)
    val positionDescription = stringResource(Res.string.workspace_autoplay_position)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                contentDescription = positionDescription
                progressBarRangeInfo = ProgressBarRangeInfo(safeValue, 0f..1f)
                setProgress { requested ->
                    if (enabled) onScrubFinished(requested.coerceIn(0f, 1f))
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var latest = (down.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    down.consume()
                    onScrub(latest)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        latest = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onScrub(latest)
                        change.consume()
                    } while (change.pressed)
                    onScrubFinished(latest)
                }
            },
    ) {
        val trackHeight = 8.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2f
        val cornerRadius = CornerRadius(trackHeight / 2f)

        drawRoundRect(
            color = background,
            topLeft = Offset(0f, trackTop),
            size = Size(size.width, trackHeight),
            cornerRadius = cornerRadius,
        )
        if (safeValue > 0f) {
            drawRoundRect(
                color = foreground,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width * safeValue, trackHeight),
                cornerRadius = cornerRadius,
            )
        }
        if (enabled) {
            drawCircle(
                color = foreground,
                radius = 5.dp.toPx(),
                center = Offset(safeValue * size.width, size.height / 2f),
            )
        }
    }
}

internal fun formatAutoPlayTime(millis: Double): String {
    val totalSeconds = (millis / 1000.0).toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
