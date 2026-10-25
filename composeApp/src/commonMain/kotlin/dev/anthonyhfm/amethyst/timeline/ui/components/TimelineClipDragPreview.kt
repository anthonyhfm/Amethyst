package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.data.ChainEffectEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.TimelineEntry
import dev.anthonyhfm.amethyst.timeline.data.resolvedPadIndex
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
internal fun TimelineClipDragPreview(entry: TimelineEntry, valid: Boolean, zoomX: Float) {
    val colors = TimelineTheme.clipColors(
        role = if (entry is AudioEntry) TimelineClipRole.Audio else TimelineClipRole.Lights,
        selected = true,
    )
    val waveformColor = TimelineTheme.audioWaveformColor()
    val dimensions = TimelineTheme.dimensions
    val shape = RoundedCornerShape(size = dimensions.clipCornerRadius)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha = if (entry.isMuted) 0.45f else 1f)
            .clip(shape = shape)
            .background(color = colors.background.copy(alpha = 0.98f))
            .border(
                width = 2.dp,
                color = if (valid) colors.border else colors.border.copy(alpha = 0.45f),
                shape = shape,
            ),
    ) {
        Text(
            text = when (entry) {
                is AudioEntry -> entry.name.ifBlank { entry.fileName }
                is MidiEntry -> entry.name
                is ChainEffectEntry -> entry.name
                else -> "Clip"
            },
            color = colors.content,
            style = Theme[typography][small],
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .height(height = dimensions.clipHeaderHeight)
                .background(color = colors.header)
                .padding(horizontal = 6.dp),
        )
        when (entry) {
            is AudioEntry -> WaveformView(
                pcmSource = entry.source()?.pcmSource(),
                sampleRate = entry.sampleRate,
                channels = entry.channels,
                bitDepth = entry.bitDepth,
                timelineStartUs = entry.startTimeUs,
                startSample = entry.clipStartSample,
                endSample = entry.clipEndSample,
                zoomLevel = zoomX,
                waveColor = waveformColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f)
                    .padding(vertical = 4.dp),
            )
            is MidiEntry -> Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(weight = 1f),
            ) {
                entry.notes.forEach { note ->
                    drawRect(
                        color = Color(red = note.led.red, green = note.led.green, blue = note.led.blue),
                        topLeft = Offset(x = note.startTimeMs * zoomX, y = (99 - note.resolvedPadIndex) / 100f * size.height),
                        size = Size(width = (note.durationMs * zoomX).coerceAtLeast(minimumValue = 1f), height = 2.dp.toPx()),
                    )
                }
            }
        }
    }
}
