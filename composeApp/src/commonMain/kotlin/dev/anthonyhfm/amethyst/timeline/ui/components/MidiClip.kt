package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun MidiClip(
    midiEntry: MidiEntry,
    zoomLevel: Float,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    gridIntervalMs: Long,
    isLightsTrack: Boolean = false,
    onDoubleClick: () -> Unit = {}
) {
    val startOffsetPx = (midiEntry.startTimeMs.toDouble() * zoomLevel.toDouble()).roundToInt()
    val widthDp = with(LocalDensity.current) { (midiEntry.durationMs.toDouble() * zoomLevel.toDouble()).toFloat().toDp() }
    val borderColor = if (isSelected) Color.White else if (isLightsTrack) Color(0xFFD4AF37) else Color(0xFFBA3C8C)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.tertiary else if (isLightsTrack) Color(0xFFFFD700) else Color(0xFFEF5698)
    val foregroundColor = if (isSelected) MaterialTheme.colorScheme.onTertiary else if (isLightsTrack) Color.Black else Color.White
    val dragOffsetPx = remember(midiEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }
    val previewStartMs by remember(dragOffsetPx.value, zoomLevel, snapEnabled) {
        derivedStateOf {
            val rawDeltaMsDouble = dragOffsetPx.value / zoomLevel
            val candidateMsDouble = midiEntry.startTimeMs.toDouble() + rawDeltaMsDouble
            val nonNegativeCandidate = candidateMsDouble.coerceAtLeast(0.0)
            if (snapEnabled && gridIntervalMs > 0) {
                val gridPxSpacing = gridIntervalMs * zoomLevel
                val thresholdPx = (gridPxSpacing * 0.35f).coerceAtLeast(5f)
                GridUtils.snapToGridWithThreshold(nonNegativeCandidate.roundToLong(), zoomLevel, WorkspaceRepository.bpm.value, WorkspaceRepository.gridType.value, thresholdPx)
            } else round(nonNegativeCandidate).toLong()
        }
    }

    val finalOffsetPx = startOffsetPx + dragOffsetPx.value.roundToInt()

    Column(
        modifier = Modifier
            .offset { IntOffset(finalOffsetPx, 0) }
            .width(widthDp)
            .height(120.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor.copy(alpha = if (isSelected) 0.96f else 0.90f))
            .then(if (isSelected) Modifier.border(1.5.dp, borderColor) else Modifier.border(1.dp, borderColor.copy(alpha = 0.85f)))
    ) {
        Text(
            text = midiEntry.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .background(borderColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .clickable { onSelectEntry() }
                .pointerInput(midiEntry.startTimeMs, zoomLevel, gridIntervalMs) {
                    detectDragGestures(
                        onDragStart = { onSelectEntry() },
                        onDragEnd = {
                            if (previewStartMs != midiEntry.startTimeMs) onMoveEntry(previewStartMs)
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
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleClick() }) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = MaterialTheme.typography.labelSmall.fontSize),
            color = foregroundColor,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(4.dp)
                .drawWithContent {
                    drawContent()
                    if (zoomLevel <= 0f) return@drawWithContent
                    val contentHeightPx = size.height
                    val noteBarMinHeightPx = 4f
                    val noteBarMaxHeightPx = 14f
                    midiEntry.notes.forEach { note ->
                        val overlapStart = maxOf(note.startTimeMs, midiEntry.startTimeMs)
                        val overlapEnd = minOf(note.endTimeMs, midiEntry.endTimeMs)
                        if (overlapEnd <= overlapStart) return@forEach
                        val relStartMs = overlapStart - midiEntry.startTimeMs
                        val relDurationMs = overlapEnd - overlapStart
                        val x = relStartMs * zoomLevel
                        val w = relDurationMs * zoomLevel
                        if (w < 0.5f) return@forEach
                        val pitchRatio = (note.pitch.coerceIn(0, 127)) / 127f
                        val y = (1f - pitchRatio) * (contentHeightPx - noteBarMaxHeightPx)
                        val barHeightPx = noteBarMinHeightPx + (noteBarMaxHeightPx - noteBarMinHeightPx) * 0.55f
                        val noteColor = Color(note.led.red, note.led.green, note.led.blue)
                        drawRect(
                            color = noteColor.copy(alpha = 0.60f),
                            topLeft = Offset(x, y),
                            size = Size(w, barHeightPx)
                        )
                        drawRect(
                            color = noteColor.copy(alpha = 0.92f),
                            topLeft = Offset(x, y),
                            size = Size(w, barHeightPx),
                            style = Stroke(width = 1.0f)
                        )
                    }
                }
        ) {
            Text(
                text = "${midiEntry.notes.size} notes",
                modifier = Modifier.align(Alignment.BottomStart).background(backgroundColor.copy(alpha = 0.35f)).padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75f),
                color = foregroundColor.copy(alpha = 0.9f),
                maxLines = 1
            )
        }
    }
}
