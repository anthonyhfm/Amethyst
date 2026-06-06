package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.util.primaryModifierShortcutLabel
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.timeline.TimelineCommandExecutor
import dev.anthonyhfm.amethyst.timeline.TimelineCommandSurface
import dev.anthonyhfm.amethyst.timeline.TimelineEditCommand
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLaneKey
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrackAutomationTarget
import dev.anthonyhfm.amethyst.timeline.data.timelineTrackRows
import dev.anthonyhfm.amethyst.timeline.ui.TimelineContextMenuAction
import dev.anthonyhfm.amethyst.timeline.ui.components.AddTrackButton
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.FullShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import dev.anthonyhfm.amethyst.ui.components.primitives.SeparatorOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tooltip
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlin.math.roundToInt

@Composable
fun TrackListView(
    tracks: List<TimelineTrack<*>>,
    verticalScrollState: ScrollState = rememberScrollState(),
    onTrackVolumeChange: (trackIndex: Int, value: Float) -> Unit = { _, _ -> },
    onTrackSoloToggle: (trackIndex: Int) -> Unit = {},
    onTrackMuteToggle: (trackIndex: Int) -> Unit = {},
    onAddLightsTrack: () -> Unit = {},
    onAddAudioTrack: () -> Unit = {},
) {
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions

    Box(
        modifier = Modifier
            .width(timelineDimensions.trackHeaderWidth)
            .fillMaxHeight()
            .zIndex(10f)
            .background(timelinePalette.laneSurfaceRaised)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawLine(
                    color = timelinePalette.shellBorder,
                    start = Offset(size.width - stroke / 2f, 0f),
                    end = Offset(size.width - stroke / 2f, size.height),
                    strokeWidth = stroke
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
                .pointerInput(Unit) {
                    detectTapGestures {
                        SelectionManager.clear()
                    }
                }
                .padding(horizontal = 6.dp)
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(timelineDimensions.laneSpacing),
        ) {
            tracks.timelineTrackRows().forEach { trackRow ->
                TrackInfo(
                    track = trackRow.track,
                    allTracks = tracks,
                    trackIndex = trackRow.trackIndex,
                    nestingLevel = trackRow.nestingLevel,
                    onTrackVolumeChange = onTrackVolumeChange,
                    onTrackSoloToggle = onTrackSoloToggle,
                    onTrackMuteToggle = onTrackMuteToggle,
                )
            }

            if (tracks.isNotEmpty()) {
                Separator(
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }

            AddTrackButton(
                onAddLightsTrack = onAddLightsTrack,
                onAddAudioTrack = onAddAudioTrack,
            )
        }

        Separator(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            orientation = SeparatorOrientation.Vertical
        )
    }
}

@Composable
fun TrackInfo(
    track: TimelineTrack<*>,
    allTracks: List<TimelineTrack<*>>,
    trackIndex: Int,
    nestingLevel: Int,
    onTrackVolumeChange: (trackIndex: Int, value: Float) -> Unit,
    onTrackSoloToggle: (trackIndex: Int) -> Unit,
    onTrackMuteToggle: (trackIndex: Int) -> Unit,
) {
    val timelinePalette = TimelineTheme.palette
    val timelineDimensions = TimelineTheme.dimensions
    val trackPresentation = track.presentation(trackIndex, allTracks)
    val trackName = track.displayName(trackIndex, allTracks)

    val selections by SelectionManager.selections.collectAsState()
    val clipboardData by ClipboardManager.clipboardData.collectAsState()
    val canPasteTracks = clipboardData is ClipboardData.TimelineTracks
    val isSelected = selections.any { it is Selectable.TimelineTrack && it.trackIndex == trackIndex }
    val selectedAutomationLane = selections
        .filterIsInstance<Selectable.TimelineAutomationLane>()
        .lastOrNull()
    val trackHeaderColors = TimelineTheme.trackHeaderColors(isSelected)
    val trackAccentColors = TimelineTheme.clipColors(trackPresentation.role, selected = false)
    val trackShape = RoundedCornerShape(timelineDimensions.clipCornerRadius)
    val accentSurface = trackAccentColors.header.copy(
        alpha = when {
            isSelected -> 0.28f
            else -> 0.18f
        }
    )
    val accentBorder = trackAccentColors.border.copy(
        alpha = when {
            isSelected -> 0.92f
            else -> 0.65f
        }
    )
    val inactiveChipContainer = trackHeaderColors.content.copy(alpha = 0.08f)
    val inactiveChipBorder = trackHeaderColors.content.copy(alpha = 0.18f)
    val inactiveChipContent = trackHeaderColors.content.copy(alpha = 0.42f)
    val activeChipContainer = trackAccentColors.border
    val activeChipBorder = trackAccentColors.border.copy(alpha = 0.96f)
    val activeChipContent = activeChipContainer.contrastForeground()
    val contextTrackIndices = TimelineCommandSurface.trackTargetsForContext(trackIndex, selections)
    val visibleTrackIndices = remember(allTracks) {
        allTracks.timelineTrackRows().map { it.trackIndex }
    }
    val contextTracks = contextTrackIndices.mapNotNull { allTracks.getOrNull(it) }
    val routedSourceTrackIndices = TimelineCommandSurface.routedSourceTrackIndices(
        trackIndex = trackIndex,
        tracks = allTracks
    )
    val renameTarget = contextTrackIndices.singleOrNull()
    val overlayAutomationLanes = track.overlayAutomationLanes()
    val stackedAutomationLanes = track.stackedAutomationLanes()
    val automationVisible = overlayAutomationLanes.isNotEmpty() || stackedAutomationLanes.isNotEmpty()
    val headerSummary = buildList {
        overlayAutomationLanes.firstOrNull()?.let { automationLane ->
            add(
                buildString {
                    append("VOL AUTO")
                    append(" · ")
                    append(automationLane.points.size)
                    append(" PTS")
                    if (!automationLane.enabled) {
                        append(" · BYPASSED")
                    }
                }
            )
        }
        if (stackedAutomationLanes.isNotEmpty()) {
            add("${stackedAutomationLanes.size} EXTRA LANE${if (stackedAutomationLanes.size == 1) "" else "S"}")
        }
    }.joinToString(separator = " · ")

    val renamingTrackIndex = remember { mutableStateOf<Int?>(null) }
    val renaming = renamingTrackIndex.value == trackIndex
    
    // React to external rename requests via SelectionManager
    LaunchedEffect(trackIndex) {
        SelectionManager.renameRequest.collect { req ->
            if (req is SelectionManager.RenameTarget.Track && req.trackIndex == trackIndex) {
                renamingTrackIndex.value = trackIndex
            }
        }
    }

    val textValue = remember { mutableStateOf(TextFieldValue(trackName)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(renaming) {
        if (renaming) {
            textValue.value = TextFieldValue(trackName)
            focusRequester.requestFocus()
        } else {
            focusRequester.freeFocus()
        }
    }

    LaunchedEffect(isSelected) {
        if (!isSelected && renaming) {
            renamingTrackIndex.value = null
            textValue.value = TextFieldValue(trackName)
        }
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TimelineAutomationLaneRowSpacing)
    ) {
        ContextMenu(
            modifier = Modifier.fillMaxWidth(),
            trigger = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(timelineDimensions.laneHeight)
                        .clip(trackShape)
                        .border(1.dp, trackHeaderColors.border, trackShape)
                        .background(trackHeaderColors.container)
                        .pointerInput(trackIndex, visibleTrackIndices) {
                            detectTapGestures(
                                onDoubleTap = {
                                    TimelineCommandSurface.requestTrackRename(trackIndex)
                                },
                                onTap = {
                                    when {
                                        ModifierKeysState.isShiftPressed -> {
                                            TimelineCommandSurface.selectTrackRange(
                                                anchorTrackIndex = SelectionManager.lastSelectedTimelineTrackIndex,
                                                targetTrackIndex = trackIndex,
                                                visibleTrackIndices = visibleTrackIndices
                                            )
                                        }

                                        ModifierKeysState.isMetaPressed || ModifierKeysState.isCtrlPressed -> {
                                            SelectionManager.toggleTimelineTrack(trackIndex)
                                        }

                                        else -> {
                                            SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
                                        }
                                    }
                                },
                            )
                        }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .clip(FullShape)
                            .background(trackAccentColors.border)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TrackHierarchyInset(
                                    nestingLevel = nestingLevel,
                                    color = accentBorder
                                )

                                if (!renaming) {
                                    Text(
                                        text = trackName,
                                        style = Theme[typography][p].copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = trackHeaderColors.content,
                                        ),
                                        maxLines = 1,
                                    )
                                } else {
                                    val customTextSelectionColors = TextSelectionColors(
                                        handleColor = trackHeaderColors.border,
                                        backgroundColor = trackHeaderColors.border.copy(alpha = 0.32f)
                                    )

                                    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                                        BasicTextField(
                                            value = textValue.value,
                                            onValueChange = { textValue.value = it },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(SmallShape)
                                                .background(trackHeaderColors.border.copy(alpha = 0.12f))
                                                .border(1.dp, trackHeaderColors.border.copy(alpha = 0.85f), SmallShape)
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                .focusRequester(focusRequester)
                                                .onFocusSelectAll(textValue)
                                                .onKeyEvent { ev ->
                                                    if (ev.key == Key.Enter) {
                                                        TimelineCommandExecutor.execute(
                                                            TimelineEditCommand.RenameTrack(
                                                                trackIndex = trackIndex,
                                                                newName = textValue.value.text
                                                            )
                                                        )
                                                        renamingTrackIndex.value = null
                                                        return@onKeyEvent true
                                                    }

                                                    if (ev.key == Key.Escape) {
                                                        renamingTrackIndex.value = null
                                                        textValue.value = TextFieldValue(trackName)
                                                        return@onKeyEvent true
                                                    }

                                                    return@onKeyEvent false
                                                },
                                            keyboardOptions = KeyboardOptions(
                                                capitalization = KeyboardCapitalization.None,
                                                autoCorrectEnabled = false,
                                                keyboardType = KeyboardType.Unspecified,
                                                imeAction = ImeAction.Done
                                            ),
                                            textStyle = Theme[typography][p].copy(
                                                color = trackHeaderColors.content,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            cursorBrush = SolidColor(trackHeaderColors.content),
                                        )
                                    }
                                }
                            }
                        }

                        if (headerSummary.isNotBlank()) {
                            Text(
                                text = headerSummary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                style = Theme[typography][small].copy(
                                    color = trackHeaderColors.content.copy(alpha = 0.66f),
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Separator()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackToggleChip(
                                    label = "M",
                                    active = track.isMuted,
                                    activeContainer = activeChipContainer,
                                    activeBorder = activeChipBorder,
                                    activeContent = activeChipContent,
                                    inactiveContainer = inactiveChipContainer,
                                    inactiveBorder = inactiveChipBorder,
                                    inactiveContent = inactiveChipContent,
                                    onClick = {
                                        SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
                                        onTrackMuteToggle(trackIndex)
                                    }
                                )
                                TrackToggleChip(
                                    label = "S",
                                    active = track.isSoloed,
                                    activeContainer = activeChipContainer,
                                    activeBorder = activeChipBorder,
                                    activeContent = activeChipContent,
                                    inactiveContainer = inactiveChipContainer,
                                    inactiveBorder = inactiveChipBorder,
                                    inactiveContent = inactiveChipContent,
                                    onClick = {
                                        SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
                                        onTrackSoloToggle(trackIndex)
                                    }
                                )
                                TrackToggleChip(
                                    label = "A",
                                    active = automationVisible,
                                    activeContainer = activeChipContainer,
                                    activeBorder = activeChipBorder,
                                    activeContent = activeChipContent,
                                    inactiveContainer = inactiveChipContainer,
                                    inactiveBorder = inactiveChipBorder,
                                    inactiveContent = inactiveChipContent,
                                    onClick = {
                                        SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
                                        TimelineCommandSurface.toggleTrackAutomationVisibility(
                                            trackIndex = trackIndex,
                                            tracks = allTracks
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Tooltip(
                                text = "${trackPresentation.label} track",
                                anchor = {
                                    TrackChromeChip(
                                        containerColor = accentSurface,
                                        borderColor = accentBorder,
                                    ) {
                                        Icon(
                                            imageVector = trackPresentation.icon,
                                            contentDescription = "${trackPresentation.label} track",
                                            tint = trackAccentColors.content,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
        ) {
            TimelineContextMenuAction(
                label = when {
                    contextTrackIndices.size > 1 -> "Toggle Mute"
                    track.isMuted -> "Unmute Track"
                    else -> "Mute Track"
                },
                shortcut = "M",
                onClick = {
                    TimelineCommandSurface.toggleTrackMute(contextTrackIndices, allTracks)
                }
            )
            TimelineContextMenuAction(
                label = when {
                    contextTrackIndices.size > 1 -> "Toggle Solo"
                    track.isSoloed -> "Unsolo Track"
                    else -> "Solo Track"
                },
                shortcut = "S",
                onClick = {
                    TimelineCommandSurface.toggleTrackSolo(contextTrackIndices, allTracks)
                }
            )
            TimelineContextMenuAction(
                label = "Toggle Automation",
                shortcut = "A",
                enabled = contextTrackIndices.size == 1,
                onClick = {
                    TimelineCommandSurface.toggleTrackAutomationVisibility(
                        trackIndex = trackIndex,
                        tracks = allTracks
                    )
                }
            )
            ContextMenuSeparator()
            TimelineContextMenuAction(
                label = "Rename Track",
                shortcut = primaryModifierShortcutLabel("R"),
                enabled = renameTarget != null,
                onClick = {
                    renameTarget?.let(TimelineCommandSurface::requestTrackRename)
                }
            )
            if (routedSourceTrackIndices.isNotEmpty()) {
                TimelineContextMenuAction(
                    label = if (routedSourceTrackIndices.size == 1) "Select Routed Source" else "Select Routed Sources",
                    onClick = {
                        TimelineCommandSurface.selectRoutedSources(
                            trackIndex = trackIndex,
                            tracks = allTracks,
                        )
                    },
                )
            }

            TimelineContextMenuAction(
                label = if (contextTrackIndices.size > 1) "Copy Tracks" else "Copy Track",
                shortcut = primaryModifierShortcutLabel("C"),
                onClick = {
                    ClipboardManager.copy(
                        contextTrackIndices.map { Selectable.TimelineTrack(trackIndex = it) }
                    )
                },
            )
            TimelineContextMenuAction(
                label = if (contextTrackIndices.size > 1) "Paste Tracks" else "Paste Track",
                shortcut = primaryModifierShortcutLabel("V"),
                enabled = canPasteTracks,
                onClick = { ClipboardManager.paste() },
            )
            TimelineContextMenuAction(
                label = if (contextTrackIndices.size > 1) "Duplicate Tracks" else "Duplicate Track",
                shortcut = primaryModifierShortcutLabel("D"),
                onClick = {
                    TimelineCommandExecutor.execute(
                        TimelineEditCommand.DuplicateTracks(contextTrackIndices)
                    )
                },
            )
            ContextMenuSeparator()
            TimelineContextMenuAction(
                label = if (contextTrackIndices.size > 1) "Delete Tracks" else "Delete Track",
                shortcut = "Delete",
                destructive = true,
                onClick = {
                    TimelineCommandExecutor.execute(
                        TimelineEditCommand.DeleteTracks(contextTrackIndices)
                    )
                }
            )
        }

        stackedAutomationLanes.forEach { automationLane ->
            val laneSelection = selectedAutomationLane
            val isLaneSelected = laneSelection?.trackIndex == trackIndex &&
                laneSelection.laneKey == automationLane.key

            TrackAutomationLaneCard(
                lane = automationLane,
                label = track.automationLaneLabel(
                    lane = automationLane,
                    allTracks = allTracks
                ),
                valueText = formatAutomationValue(
                    target = automationLane.target,
                    value = track.automationLaneBaseValue(automationLane)
                ),
                pointCount = automationLane.points.size,
                selected = isLaneSelected,
                contentColor = trackHeaderColors.content,
                accentColor = timelinePalette.automationLaneAccent,
                activeContainer = activeChipContainer,
                activeBorder = activeChipBorder,
                activeContent = activeChipContent,
                inactiveContainer = inactiveChipContainer,
                inactiveBorder = inactiveChipBorder,
                inactiveContent = inactiveChipContent,
                onSelect = {
                    SelectionManager.selectTimelineAutomationLane(
                        trackIndex = trackIndex,
                        target = automationLane.target,
                        bindingId = automationLane.bindingId
                    )
                },
                onEnabledToggle = {
                    TimelineCommandSurface.setAutomationLaneEnabled(
                        trackIndex = trackIndex,
                        lane = automationLane.key,
                        enabled = !automationLane.enabled
                    )
                },
                onHide = {
                    TimelineCommandSurface.setAutomationLaneVisibility(
                        trackIndex = trackIndex,
                        lane = automationLane.key,
                        visible = false
                    )
                }
            )
        }
    }
}

internal fun Color.contrastForeground(): Color =
    if (((red * 0.2126f) + (green * 0.7152f) + (blue * 0.0722f)) > 0.45f) {
        Color(0xFF0F172A)
    } else {
        Color.White.copy(alpha = 0.96f)
    }

@Composable
internal fun TrackAutomationLaneCard(
    lane: TimelineAutomationLane,
    label: String,
    valueText: String,
    pointCount: Int,
    selected: Boolean,
    contentColor: Color,
    accentColor: Color,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveBorder: Color,
    inactiveContent: Color,
    onSelect: () -> Unit,
    onEnabledToggle: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimelineAutomationLaneRowHeight)
            .clip(SmallShape)
            .background(
                if (selected) {
                    TimelineTheme.palette.automationLaneSurface.copy(alpha = 0.96f)
                } else {
                    TimelineTheme.palette.automationLaneSurface.copy(alpha = 0.82f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    accentColor
                } else {
                    inactiveBorder
                },
                shape = SmallShape
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(FullShape)
                .background(accentColor.copy(alpha = if (lane.enabled) 0.95f else 0.45f))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Theme[typography][small].copy(
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "$pointCount pts · $valueText",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Theme[typography][small].copy(
                    color = contentColor.copy(alpha = 0.62f)
                )
            )
        }
        TrackActionPill(
            label = "AUTO",
            active = lane.enabled,
            activeContainer = activeContainer,
            activeBorder = activeBorder,
            activeContent = activeContent,
            inactiveContainer = inactiveContainer,
            inactiveBorder = inactiveBorder,
            inactiveContent = inactiveContent,
            onClick = onEnabledToggle
        )
        TrackChromeChip(
            containerColor = inactiveContainer,
            borderColor = inactiveBorder,
            onClick = onHide
        ) {
            Text(
                text = "×",
                style = Theme[typography][small].copy(
                    color = inactiveContent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
internal fun TrackToggleChip(
    label: String,
    active: Boolean,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveBorder: Color,
    inactiveContent: Color,
    onClick: () -> Unit
) {
    TrackChromeChip(
        containerColor = if (active) activeContainer else inactiveContainer,
        borderColor = if (active) activeBorder else inactiveBorder,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = Theme[typography][small].copy(
                color = if (active) activeContent else inactiveContent,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
internal fun TrackActionPill(
    label: String,
    active: Boolean,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveBorder: Color,
    inactiveContent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(SmallShape)
            .background(if (active) activeContainer else inactiveContainer)
            .border(1.dp, if (active) activeBorder else inactiveBorder, SmallShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = Theme[typography][small].copy(
                color = if (active) activeContent else inactiveContent,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
internal fun TrackChromeChip(
    containerColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val modifier = Modifier
        .size(24.dp)
        .clip(SmallShape)
        .background(containerColor)
        .border(1.dp, borderColor, SmallShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
        content = content
    )
}

internal data class TrackPresentation(
    val defaultName: String,
    val label: String,
    val icon: ImageVector,
    val role: dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole,
)

internal fun TimelineTrack<*>.displayName(
    trackIndex: Int,
    allTracks: List<TimelineTrack<*>>
): String = name.takeIf { it.isNotBlank() } ?: presentation(trackIndex, allTracks).defaultName

internal fun TimelineTrack<*>.presentation(
    trackIndex: Int,
    allTracks: List<TimelineTrack<*>>
): TrackPresentation =
    when (this) {
        is AudioTimelineTrack -> TrackPresentation(
            defaultName = "Audio Track ${trackIndex + 1}",
            label = "Audio",
            icon = Icons.Default.Audiotrack,
            role = dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole.Audio,
        )

        is MidiTimelineTrack -> TrackPresentation(
            defaultName = "Midi Track ${trackIndex + 1}",
            label = "Midi",
            icon = Icons.Default.Lightbulb,
            role = dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole.Midi,
        )

        else -> TrackPresentation(
            defaultName = "Track ${trackIndex + 1}",
            label = "Track",
            icon = Icons.Default.Lightbulb,
            role = dev.anthonyhfm.amethyst.ui.theme.TimelineClipRole.Midi,
        )
    }

@Composable
private fun TrackHierarchyInset(
    nestingLevel: Int,
    color: Color
) {
    if (nestingLevel <= 0) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((nestingLevel * 12).dp))
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(1.dp)
                .background(color.copy(alpha = 0.55f))
        )
    }
}
