package dev.anthonyhfm.amethyst.timeline.ui.components.track

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import com.composeunstyled.Icon
import com.composeunstyled.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key as KeyboardKey
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import dev.anthonyhfm.amethyst.timeline.ui.views.automationLaneBaseValue
import dev.anthonyhfm.amethyst.timeline.ui.views.automationLaneLabel
import dev.anthonyhfm.amethyst.timeline.ui.views.overlayAutomationLanes
import dev.anthonyhfm.amethyst.timeline.ui.views.stackedAutomationLanes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.primaryModifierShortcutLabel
import dev.anthonyhfm.amethyst.timeline.TimelineCommandExecutor
import dev.anthonyhfm.amethyst.timeline.TimelineCommandSurface
import dev.anthonyhfm.amethyst.timeline.TimelineEditCommand
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.timelineTrackRows
import dev.anthonyhfm.amethyst.timeline.ui.TimelineContextMenuAction
import dev.anthonyhfm.amethyst.timeline.ui.views.TimelineAutomationLaneRowSpacing
import dev.anthonyhfm.amethyst.timeline.ui.views.formatAutomationValue
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.FullShape
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.components.primitives.Tooltip
import dev.anthonyhfm.amethyst.ui.modifier.clickableWithDoubleTap
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun TrackInfo(
    track: TimelineTrack<*>,
    allTracks: List<TimelineTrack<*>>,
    trackIndex: Int,
    nestingLevel: Int,
    onTrackVolumeChange: (trackIndex: Int, value: Float) -> Unit,
    onTrackSoloToggle: (trackIndex: Int) -> Unit,
    onTrackMuteToggle: (trackIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
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

    val accentBorder = trackAccentColors.border.copy(
        alpha = when {
            isSelected -> 1.0f
            else -> 0.85f
        }
    )
    val inactiveChipContainer = trackHeaderColors.content.copy(alpha = if (isSelected) 0.18f else 0.12f)
    val inactiveChipBorder = trackHeaderColors.content.copy(alpha = if (isSelected) 0.42f else 0.28f)
    val inactiveChipContent = trackHeaderColors.content.copy(alpha = if (isSelected) 0.95f else 0.78f)

    val muteActiveContainer = Color(0xFFEF4444)
    val muteActiveBorder = Color(0xFFDC2626)
    val muteActiveContent = Color.White

    val soloActiveContainer = Color(0xFFF59E0B)
    val soloActiveBorder = Color(0xFFD97706)
    val soloActiveContent = Color.Black

    val autoActiveContainer = Color(0xFF3B82F6)
    val autoActiveBorder = Color(0xFF2563EB)
    val autoActiveContent = Color.White

    val activeChipContainer = autoActiveContainer
    val activeChipBorder = autoActiveBorder
    val activeChipContent = autoActiveContent
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
    val headerSummary = buildList<String> {
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
                    modifier = modifier
                        .fillMaxWidth()
                        .height(timelineDimensions.laneHeight)
                        .clip(trackShape)
                        .border(1.dp, trackHeaderColors.border, trackShape)
                        .background(trackHeaderColors.container)
                        .clickableWithDoubleTap(
                            onSingleClick = { isShiftPressed, isCmdOrCtrlPressed ->
                                when {
                                    isShiftPressed -> {
                                        TimelineCommandSurface.selectTrackRange(
                                            anchorTrackIndex = SelectionManager.lastSelectedTimelineTrackIndex,
                                            targetTrackIndex = trackIndex,
                                            visibleTrackIndices = visibleTrackIndices
                                        )
                                    }

                                    isCmdOrCtrlPressed -> {
                                        SelectionManager.toggleTimelineTrack(trackIndex)
                                    }

                                    else -> {
                                        SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
                                    }
                                }
                            },
                            onDoubleClick = {
                                TimelineCommandSurface.requestTrackRename(trackIndex)
                            }
                        )
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
                                .background(if (isSelected) Theme[colors][background] else trackAccentColors.border)
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
                                        val activeContentColor = if (isSelected) Theme[colors][selectionForeground] else trackHeaderColors.content
                                        val customTextSelectionColors = TextSelectionColors(
                                            handleColor = activeContentColor,
                                            backgroundColor = activeContentColor.copy(alpha = 0.35f)
                                        )

                                        CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                                            BasicTextField(
                                                value = textValue.value,
                                                onValueChange = { textValue.value = it },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(SmallShape)
                                                    .background(
                                                        if (isSelected) Theme[colors][selectionForeground].copy(alpha = 0.18f)
                                                        else trackHeaderColors.content.copy(alpha = 0.10f)
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Theme[colors][selectionForeground].copy(alpha = 0.60f)
                                                        else trackHeaderColors.content.copy(alpha = 0.35f),
                                                        SmallShape
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                                    .focusRequester(focusRequester)
                                                    .onFocusSelectAll(textValue)
                                                    .onKeyEvent { ev ->
                                                        if (ev.key == KeyboardKey.Enter) {
                                                            TimelineCommandExecutor.execute(
                                                                TimelineEditCommand.RenameTrack(
                                                                    trackIndex = trackIndex,
                                                                    newName = textValue.value.text
                                                                )
                                                            )
                                                            renamingTrackIndex.value = null
                                                            return@onKeyEvent true
                                                        }

                                                        if (ev.key == KeyboardKey.Escape) {
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
                                                    color = activeContentColor,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                cursorBrush = SolidColor(activeContentColor),
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
                                        activeContainer = muteActiveContainer,
                                        activeBorder = muteActiveBorder,
                                        activeContent = muteActiveContent,
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
                                        activeContainer = soloActiveContainer,
                                        activeBorder = soloActiveBorder,
                                        activeContent = soloActiveContent,
                                        inactiveContainer = inactiveChipContainer,
                                        inactiveBorder = inactiveChipBorder,
                                        inactiveContent = inactiveChipContent,
                                        onClick = {
                                            SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
                                            onTrackSoloToggle(trackIndex)
                                        }
                                    )

                                    if (track is AudioTimelineTrack) {
                                        TrackToggleChip(
                                            label = "A",
                                            active = automationVisible,
                                            activeContainer = autoActiveContainer,
                                            activeBorder = autoActiveBorder,
                                            activeContent = autoActiveContent,
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
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Tooltip(
                                    text = "${trackPresentation.label} track",
                                    anchor = {
                                        TrackChromeChip(
                                            containerColor = trackAccentColors.border,
                                            borderColor = trackAccentColors.border,
                                        ) {
                                            Icon(
                                                imageVector = trackPresentation.icon,
                                                contentDescription = "${trackPresentation.label} track",
                                                tint = Theme[colors][background],
                                                modifier = Modifier.size(14.dp)
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
