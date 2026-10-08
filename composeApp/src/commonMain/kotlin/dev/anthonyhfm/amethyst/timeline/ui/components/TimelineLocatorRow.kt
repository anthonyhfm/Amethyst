package dev.anthonyhfm.amethyst.timeline.ui.components

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.TimelineLocator
import dev.anthonyhfm.amethyst.timeline.locators.TimelineLocatorRepository
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import dev.anthonyhfm.amethyst.timeline.viewport.EditorViewportState
import dev.anthonyhfm.amethyst.ui.components.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.*
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun TimelineLocatorRow(
    viewport: EditorViewportState,
    bpm: Double,
    gridType: GridUtils.GridType,
) {
    val locators by TimelineLocatorRepository.locators.collectAsState()
    var menuOpen by remember { mutableStateOf(value = false) }
    var editingId by remember { mutableStateOf<String?>(value = null) }
    val defaultName = stringResource(resource = Res.string.locators_default_name)
    val palette = TimelineTheme.palette
    val dimensions = TimelineTheme.dimensions
    val textMeasurer = rememberTextMeasurer()
    val latestViewport by rememberUpdatedState(newValue = viewport)
    val latestLocators by rememberUpdatedState(newValue = locators)
    var draggingId by remember { mutableStateOf<String?>(value = null) }
    var dragTimeMs by remember { mutableLongStateOf(value = 0L) }

    fun create(timeMs: Long) {
        val locator = TimelineLocatorRepository.controller.create(
            name = "$defaultName ${latestLocators.size + 1}",
            timeMs = timeMs,
            bpm = bpm,
        )
        editingId = locator?.id
    }

    fun timeAt(x: Float): Long = GridUtils.snapToGrid(
        timeMs = latestViewport.screenToTimeMs(screenX = x).roundToLong().coerceAtLeast(minimumValue = 0L),
        zoomLevel = latestViewport.zoomX,
        bpm = bpm,
        gridType = gridType,
    )

    fun locatorAt(x: Float): TimelineLocator? = latestLocators
        .minByOrNull { abs(latestViewport.timeMsToScreenX(timeMs = it.timeMs(bpm = bpm).toDouble()) - x) }
        ?.takeIf { abs(latestViewport.timeMsToScreenX(timeMs = it.timeMs(bpm = bpm).toDouble()) - x) <= 14f }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height = 32.dp)
            .background(color = palette.rulerAccent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownMenu(
            expanded = menuOpen,
            onExpandRequest = { menuOpen = true },
            onDismissRequest = { menuOpen = false },
            modifier = Modifier
                .width(width = dimensions.trackHeaderWidth),
        ) {
            Button(
                onClick = { menuOpen = !menuOpen },
                modifier = Modifier
                    .fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
            ) {
                Text(text = stringResource(resource = Res.string.locators_menu))
            }

            DropdownMenuContent(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(state = rememberScrollState()),
            ) {
                ContextMenuItem(
                    label = stringResource(resource = Res.string.locators_add),
                    onClick = {
                        menuOpen = false
                        create(timeMs = TimelineRepository.playheadPositionMs.value)
                    },
                )
                locators.forEach { locator ->
                    ContextMenuItem(
                        label = locator.name,
                        onClick = {
                            menuOpen = false
                            editingId = locator.id
                        },
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .weight(weight = 1f)
                .fillMaxHeight()
                .pointerInput(bpm, gridType) {
                    detectTapGestures(
                        onTap = { offset ->
                            val locator = locatorAt(x = offset.x)
                            if (locator != null) {
                                TimelineLocatorRepository.jump(id = locator.id)
                            } else {
                                TimelineRepository.setPlayheadPosition(positionMs = timeAt(x = offset.x))
                            }
                        },
                        onDoubleTap = { offset ->
                            val locator = locatorAt(x = offset.x)
                            if (locator == null) {
                                create(timeMs = timeAt(x = offset.x))
                            } else {
                                editingId = locator.id
                            }
                        },
                    )
                }
                .pointerInput(bpm, gridType) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingId = locatorAt(x = offset.x)?.id
                            dragTimeMs = timeAt(x = offset.x)
                        },
                        onDragEnd = {
                            draggingId?.let { id ->
                                TimelineLocatorRepository.controller.move(id = id, timeMs = dragTimeMs, bpm = bpm)
                            }
                            draggingId = null
                        },
                        onDragCancel = { draggingId = null },
                        onDrag = { change, _ ->
                            if (draggingId != null) {
                                change.consume()
                                dragTimeMs = timeAt(x = change.position.x)
                            }
                        },
                    )
                },
        ) {
            drawLine(
                color = palette.shellBorder,
                start = Offset(x = 0f, y = size.height),
                end = Offset(x = size.width, y = size.height),
                strokeWidth = 1.dp.toPx(),
            )
            locators.forEach { locator ->
                val timeMs = if (locator.id == draggingId) dragTimeMs else locator.timeMs(bpm = bpm)
                val x = viewport.timeMsToScreenX(timeMs = timeMs.toDouble())
                if (x >= 0f && x <= size.width) {
                    drawLine(
                        color = palette.playhead,
                        start = Offset(x = x, y = 2.dp.toPx()),
                        end = Offset(x = x, y = size.height),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawRect(
                        color = palette.playhead,
                        topLeft = Offset(x = x, y = 2.dp.toPx()),
                        size = Size(width = 7.dp.toPx(), height = 6.dp.toPx()),
                    )
                    val nextX = locators
                        .filter { it.id != locator.id && it.beat > locator.beat }
                        .minOfOrNull { viewport.timeMsToScreenX(timeMs = it.timeMs(bpm = bpm).toDouble()) }
                        ?: size.width
                    val availableWidth = (nextX - x - 12.dp.toPx()).toInt().coerceAtLeast(minimumValue = 0)
                    if (availableWidth > 16) {
                        val label = textMeasurer.measure(
                            text = AnnotatedString(text = locator.name),
                            style = TextStyle(color = palette.rulerText, fontSize = 11.sp),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = availableWidth),
                        )
                        drawText(
                            textLayoutResult = label,
                            topLeft = Offset(x = x + 10.dp.toPx(), y = 8.dp.toPx()),
                        )
                    }
                }
            }
        }
    }

    val editing = locators.firstOrNull { it.id == editingId }
    if (editing != null) {
        TimelineLocatorDialog(
            locator = editing,
            onDismiss = { editingId = null },
        )
    }
}

@Composable
private fun TimelineLocatorDialog(
    locator: TimelineLocator,
    onDismiss: () -> Unit,
) {
    val state = rememberDialogState(initiallyVisible = true)
    var name by remember(locator.id) { mutableStateOf(value = locator.name) }
    var beatText by remember(locator.id) { mutableStateOf(value = (locator.beat + 1.0).toString()) }
    val beat = beatText.replace(oldChar = ',', newChar = '.').toDoubleOrNull()?.minus(other = 1.0)
    val valid = name.isNotBlank() && beat != null && beat.isFinite() && beat >= 0.0

    Dialog(state = state, onDismiss = onDismiss) {
        DialogContent {
            DialogTitle(text = stringResource(resource = Res.string.locators_edit))
            Text(text = stringResource(resource = Res.string.locators_name))
            Input(value = name, onValueChange = { name = it })
            Text(text = stringResource(resource = Res.string.locators_beat))
            Input(value = beatText, onValueChange = { beatText = it })
            DialogDescription(text = stringResource(resource = Res.string.locators_help))
            if (!valid) {
                Text(text = stringResource(resource = Res.string.locators_invalid))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                Button(
                    onClick = {
                        TimelineLocatorRepository.jump(id = locator.id)
                        onDismiss()
                    },
                    variant = ButtonVariant.Secondary,
                ) {
                    Text(text = stringResource(resource = Res.string.locators_jump))
                }
                Button(
                    onClick = {
                        TimelineLocatorRepository.controller.delete(id = locator.id)
                        onDismiss()
                    },
                    variant = ButtonVariant.Destructive,
                ) {
                    Text(text = stringResource(resource = Res.string.locators_delete))
                }
            }
            DialogFooter {
                Button(onClick = onDismiss, variant = ButtonVariant.Ghost) {
                    Text(text = stringResource(resource = Res.string.locators_cancel))
                }
                Button(
                    onClick = {
                        if (valid && beat != null) {
                            TimelineLocatorRepository.controller.edit(id = locator.id, name = name, beat = beat)
                            onDismiss()
                        }
                    },
                    enabled = valid,
                ) {
                    Text(text = stringResource(resource = Res.string.locators_save))
                }
            }
        }
    }
}
