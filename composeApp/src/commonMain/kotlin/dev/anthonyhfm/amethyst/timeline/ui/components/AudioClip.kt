package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.round

@Composable
fun AudioClip(
    audioEntry: AudioEntry,
    zoomLevel: Float,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    gridIntervalMs: Long
) {
    val startOffsetPx = (audioEntry.startTimeMs.toDouble() * zoomLevel.toDouble()).roundToInt()
    val widthDp = with(LocalDensity.current) { (audioEntry.durationMs.toDouble() * zoomLevel.toDouble()).toFloat().toDp() }
    val borderColor = if (isSelected) Color.White else Color(0xFF3C3CBA)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF5656EF)
    val foregroundColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
    val dragOffsetPx = remember(audioEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }
    val previewStartMs by remember(dragOffsetPx.value, zoomLevel, snapEnabled) {
        derivedStateOf {
            val rawDeltaMsDouble = dragOffsetPx.value / zoomLevel
            val candidateMsDouble = audioEntry.startTimeMs.toDouble() + rawDeltaMsDouble
            val nonNegativeCandidate = candidateMsDouble.coerceAtLeast(0.0)
            if (snapEnabled && gridIntervalMs > 0) {
                val gridPxSpacing = gridIntervalMs * zoomLevel
                val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                GridUtils.snapToGridWithThreshold(nonNegativeCandidate.roundToLong(), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
            } else round(nonNegativeCandidate).toLong()
        }
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(startOffsetPx + dragOffsetPx.value.roundToInt(), 0) }
            .clip(RoundedCornerShape(6.dp))
            .height(120.dp)
            .width(widthDp)
            .background(backgroundColor.copy(alpha = if (isSelected) 0.96f else 0.90f))
            .then(if (isSelected) Modifier.border(1.5.dp, borderColor) else Modifier)
    ) {
        Text(
            text = audioEntry.fileName.substringBeforeLast('.'),
            modifier = Modifier
                .fillMaxWidth()
                .background(borderColor)
                .clickable { onSelectEntry() }
                .pointerInput(audioEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                    detectDragGestures(
                        onDragStart = { onSelectEntry() },
                        onDragEnd = {
                            if (previewStartMs != audioEntry.startTimeMs) onMoveEntry(previewStartMs)
                            dragOffsetPx.value = 0f
                            snapEnabled = true
                        },
                        onDragCancel = { dragOffsetPx.value = 0f; snapEnabled = true },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx.value += dragAmount.x
                        }
                    )
                }
                .padding(4.dp),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
            color = if (isSelected) Color.Black else Color.White,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            WaveformView(
                modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
                waveColor = foregroundColor,
                signal = Signal.AudioSignal(
                    origin = null,
                    rawData = audioEntry.rawData,
                    bitDepth = audioEntry.bitDepth,
                    channels = audioEntry.channels,
                    sampleRate = audioEntry.sampleRate
                )
            )
        }
    }
}
