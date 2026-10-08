package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronsUpDown
import com.composables.icons.lucide.ChevronsDownUp
import com.composables.icons.lucide.Magnet
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ListFilter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ZoomIn
import com.composables.icons.lucide.ZoomOut
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.contract.GridResolution
import dev.anthonyhfm.amethyst.timeline.contract.TimelineEditorTool
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DropdownMenuRadioItem
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import org.jetbrains.compose.resources.stringResource

@Composable
fun PianoRollWorkspaceControls(
    mode: PianoRollWorkspaceMode,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkspaceToolbarSurface {
            WorkspaceToolbarIconButton(
                onClick = { mode.togglePlayback() },
                imageVector = if (mode.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (mode.isPlaying) {
                    stringResource(resource = Res.string.timeline_playback_pause)
                } else {
                    stringResource(resource = Res.string.timeline_playback_play)
                },
                variant = if (mode.isPlaying) ButtonVariant.Default else ButtonVariant.Secondary,
                enabled = mode.currentEntry != null,
            )
        }

        WorkspaceToolbarSurface {
            val drawEnabled = mode.activeTool == TimelineEditorTool.DRAW
            WorkspaceToolbarIconButton(
                onClick = {
                    mode.activeTool = if (drawEnabled) {
                        TimelineEditorTool.NORMAL
                    } else {
                        TimelineEditorTool.DRAW
                    }
                },
                imageVector = Lucide.Pencil,
                contentDescription = stringResource(resource = Res.string.workspace_topappbar_draw_mode),
                modifier = Modifier
                    .semantics { selected = drawEnabled },
                variant = if (drawEnabled) ButtonVariant.Default else ButtonVariant.Ghost,
            )
            WorkspaceToolbarIconButton(
                onClick = { mode.foldPads = !mode.foldPads },
                imageVector = Lucide.ListFilter,
                contentDescription = stringResource(resource = Res.string.workspace_topappbar_fold_pads),
                modifier = Modifier
                    .semantics { selected = mode.foldPads },
                variant = if (mode.foldPads) ButtonVariant.Default else ButtonVariant.Ghost,
            )
        }

        WorkspaceToolbarSurface {
            WorkspaceToolbarIconButton(
                onClick = { mode.snapEnabled = !mode.snapEnabled },
                imageVector = Lucide.Magnet,
                contentDescription = stringResource(resource = Res.string.piano_roll_snap) + " (Ctrl/Cmd+4)",
                modifier = Modifier
                    .semantics { selected = mode.snapEnabled },
                variant = if (mode.snapEnabled) ButtonVariant.Default else ButtonVariant.Ghost,
            )
        }

        PianoRollGridPicker(mode = mode)

        WorkspaceToolbarSurface {
            WorkspaceToolbarIconButton(
                onClick = { mode.zoomOut() },
                imageVector = Lucide.ZoomOut,
                contentDescription = stringResource(resource = Res.string.workspace_topappbar_zoom_out),
                enabled = mode.canZoom,
            )
            WorkspaceToolbarIconButton(
                onClick = { mode.zoomIn() },
                imageVector = Lucide.ZoomIn,
                contentDescription = stringResource(resource = Res.string.workspace_topappbar_zoom_in),
                enabled = mode.canZoom,
            )
            WorkspaceToolbarIconButton(
                onClick = { mode.zoomToFit() },
                imageVector = Lucide.Maximize2,
                contentDescription = stringResource(resource = Res.string.workspace_topappbar_zoom_fit) + " (X)",
                enabled = mode.canZoom,
            )
        }

        WorkspaceToolbarSurface {
            WorkspaceToolbarIconButton(
                onClick = { mode.zoomNotesOut() },
                imageVector = Lucide.ChevronsDownUp,
                contentDescription = stringResource(resource = Res.string.piano_roll_note_zoom_out),
                enabled = mode.canZoom,
            )
            WorkspaceToolbarIconButton(
                onClick = { mode.zoomNotesIn() },
                imageVector = Lucide.ChevronsUpDown,
                contentDescription = stringResource(resource = Res.string.piano_roll_note_zoom_in),
                enabled = mode.canZoom,
            )
        }
    }
}

@Composable
private fun PianoRollGridPicker(mode: PianoRollWorkspaceMode) {
    var gridMenuExpanded by remember(key1 = mode) { mutableStateOf(value = false) }
    val gridLabel = if (mode.gridResolutionLocked) {
        pianoRollGridResolutionLabel(resolution = mode.gridResolution)
    } else {
        stringResource(resource = Res.string.workspace_topappbar_grid_auto)
    }

    WorkspaceToolbarSurface {
        DropdownMenu(
            expanded = gridMenuExpanded,
            onExpandRequest = { gridMenuExpanded = true },
            onDismissRequest = { gridMenuExpanded = false },
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val hovered by interactionSource.collectIsHoveredAsState()
            val contentColor = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]

            UnstyledButton(
                onClick = { gridMenuExpanded = !gridMenuExpanded },
                shape = SmallShape,
                interactionSource = interactionSource,
                indication = null,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(height = WorkspaceToolbarControlHeight)
                    .clip(shape = SmallShape)
                    .background(color = if (hovered) Theme[colors][accent] else Color.Transparent),
            ) {
                Text(
                    text = gridLabel,
                    style = Theme[typography][small].copy(color = contentColor),
                )
                Spacer(modifier = Modifier.width(width = 4.dp))
                Icon(
                    imageVector = Lucide.ChevronDown,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(size = 10.dp),
                )
            }

            DropdownMenuContent(
                expanded = gridMenuExpanded,
                onDismissRequest = { gridMenuExpanded = false },
            ) {
                DropdownMenuRadioItem(
                    selected = !mode.gridResolutionLocked,
                    onClick = {
                        mode.gridResolutionLocked = false
                        gridMenuExpanded = false
                    },
                ) {
                    Text(text = stringResource(resource = Res.string.workspace_topappbar_grid_auto))
                }

                GridResolution.entries.forEach { resolution ->
                    DropdownMenuRadioItem(
                        selected = mode.gridResolutionLocked && mode.gridResolution == resolution,
                        onClick = {
                            mode.gridResolution = resolution
                            mode.gridResolutionLocked = true
                            gridMenuExpanded = false
                        },
                    ) {
                        Text(text = pianoRollGridResolutionLabel(resolution = resolution))
                    }
                }
            }
        }
    }
}

@Composable
private fun pianoRollGridResolutionLabel(resolution: GridResolution): String = when (resolution) {
    GridResolution.Quarter -> stringResource(resource = Res.string.workspace_topappbar_grid_quarter)
    GridResolution.Eighth -> stringResource(resource = Res.string.workspace_topappbar_grid_eighth)
    GridResolution.Sixteenth -> stringResource(resource = Res.string.workspace_topappbar_grid_sixteenth)
    GridResolution.ThirtySecond -> stringResource(resource = Res.string.workspace_topappbar_grid_thirty_second)
    else -> resolution.label
}
