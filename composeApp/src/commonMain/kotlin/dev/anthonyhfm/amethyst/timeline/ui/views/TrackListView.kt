package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.LightsTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.ui.components.AddTrackButton
import dev.anthonyhfm.amethyst.ui.modifier.onFocusSelectAll
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.dropDownMenu

@Composable
fun TrackListView(
    tracks: List<TimelineTrack<*>>,
    onAddLightsTrack: () -> Unit = {},
    onAddAudioTrack: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .zIndex(10f)
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tracks.forEachIndexed { index, track ->
            TrackInfo(track = track, trackIndex = index)
        }

        AddTrackButton(
            onAddLightsTrack = onAddLightsTrack,
            onAddAudioTrack = onAddAudioTrack
        )
    }
}

@Composable
fun TrackInfo(
    track: TimelineTrack<*>,
    trackIndex: Int
) {
    val defaultTrackName = when (track) {
        is AudioTimelineTrack -> "Audio Track ${trackIndex + 1}"
        is LightsTimelineTrack -> "Lights Track ${trackIndex + 1}"
        else -> "Track ${trackIndex + 1}"
    }
    
    // Use the track's name if set, otherwise use default
    val trackName = if (track.name.isNotEmpty()) track.name else defaultTrackName

    val selections by SelectionManager.selections.collectAsState()
    val isSelected = selections.any { it is Selectable.TimelineTrack && it.trackIndex == trackIndex }
    
    var showRightClickMenu by remember { mutableStateOf(false) }
    var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current.density

    val renamingTrackIndex = remember { mutableStateOf<Int?>(null) }
    val renaming = renamingTrackIndex.value == trackIndex
    
    // React to external rename requests via SelectionManager
    val renameRequest = SelectionManager.renameRequest.collectAsState().value
    LaunchedEffect(renameRequest) {
        renameRequest?.let { req ->
            if (req is SelectionManager.RenameTarget.Track && req.trackIndex == trackIndex) {
                renamingTrackIndex.value = trackIndex
                SelectionManager.renameRequest.value = null
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
        modifier = Modifier
            .width(200.dp)
            .height(120.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            )
            .clickable {
                SelectionManager.select(Selectable.TimelineTrack(trackIndex = trackIndex))
            }
            .rightClickable {
                rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
                showRightClickMenu = true
            }
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!renaming) {
                Text(
                    text = trackName,
                    style = MaterialTheme.typography.labelLarge.copy(
                        lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                            .focusRequester(focusRequester)
                            .onFocusSelectAll(textValue)
                            .onKeyEvent { ev ->
                                if (ev.key == Key.Enter) {
                                    // Save the new name
                                    track.name = textValue.value.text
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
                        textStyle = MaterialTheme.typography.labelLarge.copy(
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        ),
                        cursorBrush = SolidColor(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier
                .weight(1f)
        )

        Icon(
            imageVector = when (track) {
                is AudioTimelineTrack -> Icons.Default.Audiotrack
                is LightsTimelineTrack -> Icons.Default.Lightbulb
                else -> Icons.Default.Lightbulb
            },
            contentDescription = "Track Type Icon",
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.End)
                .padding(8.dp)
                .size(24.dp)
        )
    }

    TrackContextMenu(
        trackIndex = trackIndex,
        track = track,
        visible = showRightClickMenu,
        offset = rightClickMenuOffset,
        onDismiss = { showRightClickMenu = false }
    )
}

@Composable
fun TrackContextMenu(
    trackIndex: Int,
    track: TimelineTrack<*>,
    visible: Boolean,
    offset: DpOffset,
    onDismiss: () -> Unit
) {
    Dropdown(
        isOpen = visible,
        menu = dropDownMenu {
            item("duplicate", "Duplicate Track") {
                icon(Icons.Default.ControlPointDuplicate)
            }

            horizontalDivider()

            item("delete", "Delete Track") {
                icon(Icons.Default.Delete)
            }
        },
        offset = offset,
        onItemSelected = {
            when (it) {
                "duplicate" -> {
                    val duplicated = TimelineRepository.duplicateTrack(trackIndex)
                    if (duplicated != null) {
                        UndoManager.addAction(
                            UndoableAction.TrackDuplication(
                                originalIndex = trackIndex,
                                duplicatedIndex = trackIndex + 1,
                                duplicatedTrack = duplicated
                            )
                        )
                    }
                }
                "delete" -> {
                    val removed = TimelineRepository.tracks.value.getOrNull(trackIndex)
                    if (removed != null) {
                        UndoManager.addAction(
                            UndoableAction.TrackRemoval(
                                trackIndex = trackIndex,
                                track = removed
                            )
                        )
                        TimelineRepository.removeTrack(trackIndex)
                        SelectionManager.clear()
                    }
                }
            }
            onDismiss()
        },
        onDismiss = {
            onDismiss()
        }
    )
}