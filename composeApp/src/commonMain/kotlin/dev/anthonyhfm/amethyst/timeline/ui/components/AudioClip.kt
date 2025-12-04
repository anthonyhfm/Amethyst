package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.ui.components.WaveformView
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ScrollState
import dev.anthonyhfm.amethyst.timeline.utils.computeStrictGridTime
import kotlin.math.round

@Composable
fun AudioClip(
    audioEntry: AudioEntry,
    zoomLevel: Float,
    isSelected: Boolean,
    onSelectEntry: () -> Unit,
    onMoveEntry: (newStartMs: Long) -> Unit,
    gridIntervalMs: Long,
    trackIndex: Int,
    entryStartMs: Long,
    scrollState: ScrollState,
    bpm: Double,
    gridType: GridUtils.GridType
) {
    var rangeActive by remember { mutableStateOf(false) }
    var rangeStartMs by remember { mutableStateOf<Long?>(null) }
    var rangeEndMs by remember { mutableStateOf<Long?>(null) }

    val startOffsetPx = (audioEntry.startTimeMs.toDouble() * zoomLevel.toDouble()).roundToInt()
    val widthDp = with(LocalDensity.current) { (audioEntry.durationMs.toDouble() * zoomLevel.toDouble()).toFloat().toDp() }
    val borderColor = if (isSelected) Color.White else Color(0xFF3C3CBA)
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF5656EF)
    val foregroundColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White
    val dragOffsetPx = remember(audioEntry.startTimeMs) { mutableStateOf(0f) }
    var snapEnabled by remember { mutableStateOf(true) }
    
    // Rename support
    val displayName = if (audioEntry.name.isNotEmpty()) audioEntry.name else audioEntry.fileName.substringBeforeLast('.')
    val renamingEntryIndex = remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val renaming = renamingEntryIndex.value == Pair(trackIndex, entryStartMs)
    
    val renameRequest = SelectionManager.renameRequest.collectAsState().value
    LaunchedEffect(renameRequest) {
        renameRequest?.let { req ->
            if (req is SelectionManager.RenameTarget.TimelineEntry && 
                req.trackIndex == trackIndex && 
                req.entryStartMs == entryStartMs) {
                renamingEntryIndex.value = Pair(trackIndex, entryStartMs)
                SelectionManager.renameRequest.value = null
            }
        }
    }

    val textValue = remember { mutableStateOf(TextFieldValue(displayName)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(renaming) {
        if (renaming) {
            textValue.value = TextFieldValue(displayName)
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    LaunchedEffect(isSelected) {
        if (!isSelected && renaming) {
            renamingEntryIndex.value = null
            textValue.value = TextFieldValue(displayName)
        }
    }
    
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
        if (!renaming) {
            Text(
                text = displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(borderColor)
                    .pointerInput(audioEntry.startTimeMs) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press) {
                                    val change = event.changes.firstOrNull()
                                    if (change != null) {
                                        val isShiftPressed = event.keyboardModifiers.isShiftPressed
                                        if (isShiftPressed) {
                                            // Multi-select mode
                                            SelectionManager.select(
                                                Selectable.TimelineEntryItem(trackIndex = trackIndex, entryStartMs = entryStartMs),
                                                single = false
                                            )
                                        } else {
                                            // Single select mode
                                            onSelectEntry()
                                        }
                                        change.consume()
                                    }
                                }
                            }
                        }
                    }
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
        } else {
            val customTextSelectionColors = TextSelectionColors(
                handleColor = MaterialTheme.colorScheme.secondaryContainer,
                backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            )

            CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                BasicTextField(
                    value = textValue.value,
                    onValueChange = { textValue.value = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(borderColor)
                        .focusRequester(focusRequester)
                        .onFocusSelectAll(textValue)
                        .onKeyEvent { ev ->
                            if (ev.key == Key.Enter) {
                                audioEntry.name = textValue.value.text
                                renamingEntryIndex.value = null
                                return@onKeyEvent true
                            }

                            if (ev.key == Key.Escape) {
                                renamingEntryIndex.value = null
                                textValue.value = TextFieldValue(displayName)
                                return@onKeyEvent true
                            }

                            return@onKeyEvent false
                        }
                        .padding(4.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Unspecified,
                        imeAction = ImeAction.Done
                    ),
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                        color = if (isSelected) Color.Black else Color.White
                    ),
                    cursorBrush = SolidColor(if (isSelected) Color.Black else Color.White),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(audioEntry.startTimeMs, zoomLevel, bpm, gridType) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Calculate absolute position considering scroll and clip offset
                            val clipStartPx = audioEntry.startTimeMs.toDouble() * zoomLevel.toDouble()
                            val absoluteX = (clipStartPx + offset.x - scrollState.value.toDouble()).toFloat()
                            val startMs = computeStrictGridTime(absoluteX + scrollState.value.toFloat(), scrollState, zoomLevel, bpm, gridType)
                            rangeStartMs = startMs
                            rangeEndMs = startMs
                            rangeActive = true
                        },
                        onDrag = { change, _ ->
                            if (rangeActive && rangeStartMs != null) {
                                val clipStartPx = audioEntry.startTimeMs.toDouble() * zoomLevel.toDouble()
                                val absoluteX = (clipStartPx + change.position.x - scrollState.value.toDouble()).toFloat()
                                val currentMs = computeStrictGridTime(absoluteX + scrollState.value.toFloat(), scrollState, zoomLevel, bpm, gridType)
                                if (currentMs != rangeEndMs) {
                                    rangeEndMs = currentMs
                                }
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                                val start = rangeStartMs!!.coerceAtLeast(0L)
                                val end = rangeEndMs!!.coerceAtLeast(0L)
                                val normalizedStart = kotlin.math.min(start, end)
                                val normalizedEnd = kotlin.math.max(start, end)
                                if (normalizedEnd > normalizedStart) {
                                    SelectionManager.select(
                                        Selectable.TimelineRange(
                                            trackIndex = trackIndex,
                                            startMs = normalizedStart,
                                            endMs = normalizedEnd
                                        )
                                    )
                                }
                            }
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                        },
                        onDragCancel = {
                            rangeActive = false
                            rangeStartMs = null
                            rangeEndMs = null
                        }
                    )
                }
                .drawWithContent {
                    drawContent()
                    // Draw range selection overlay
                    if (rangeActive && rangeStartMs != null && rangeEndMs != null) {
                        val start = kotlin.math.min(rangeStartMs!!, rangeEndMs!!)
                        val end = kotlin.math.max(rangeStartMs!!, rangeEndMs!!)

                        // Convert to clip-relative coordinates
                        val clipStartMs = audioEntry.startTimeMs
                        val clipEndMs = audioEntry.startTimeMs + audioEntry.durationMs

                        // Clip the range to visible portion
                        val visibleStart = start.coerceIn(clipStartMs, clipEndMs)
                        val visibleEnd = end.coerceIn(clipStartMs, clipEndMs)

                        if (visibleEnd > visibleStart) {
                            val relStartMs = visibleStart - clipStartMs
                            val relEndMs = visibleEnd - clipStartMs
                            val startX = relStartMs * zoomLevel
                            val width = (relEndMs - relStartMs) * zoomLevel

                            drawRect(
                                color = Color(0x5533AAFF),
                                topLeft = Offset(startX, 0f),
                                size = Size(width, size.height)
                            )
                        }
                    }
                }
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
                ),
                totalDurationMs = audioEntry.sourceDurationMs,
                startMs = audioEntry.sourceStartMs,
                durationMs = audioEntry.durationMs,
                zoomLevel = zoomLevel
            )
        }
    }
}
