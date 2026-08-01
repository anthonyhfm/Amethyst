package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.pianoroll.PianoRollChainDevice
import dev.anthonyhfm.amethyst.timeline.TimelineRepository
import dev.anthonyhfm.amethyst.timeline.TimelineViewModel
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollBarOrientation
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainDevicePicker
import dev.anthonyhfm.amethyst.workspace.chain.ui.ChainView

/**
 * An inline chain editor for a Timeline Chain Effect.
 *
 * It intentionally follows [dev.anthonyhfm.amethyst.workspace.chain.ui.WorkspaceChainEditor]'s
 * geometry: processors retain the complete 280dp editor height, while an empty source gets a
 * deliberately calm 280dp start canvas. The only controls that are not part of the chain are
 * overlaid so they never squeeze the device row.
 */
@Composable
fun TimelineChainEffectPanel(
    viewModel: TimelineViewModel,
    clipId: String,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsState()
    val trackIndex = tracks.mapIndexedNotNull { index, track ->
        (track as? MidiTimelineTrack)
            ?.chainEffectEntries?.values?.firstOrNull { it.clipId == clipId }
            ?.let { index }
    }.firstOrNull() ?: return
    val runtime = TimelineRepository.chainEffectRuntime(clipId) ?: return
    val dragAndDropState = rememberDragAndDropState<GenericChainDevice<*>>()
    val scrollState = rememberScrollAreaState()
    var sourcePickerVisible by remember(clipId) { mutableStateOf(false) }
    val selectedProcessor = SelectionManager.selections.collectAsState().value
        .filterIsInstance<Selectable.ChainDevice>()
        .lastOrNull { it.parent === runtime.processors }

    val themeBorder = Theme[colors][border]

    val track = tracks.getOrNull(trackIndex)
    val trackName = track?.name ?: "Track ${trackIndex + 1}"
    val entry = (track as? MidiTimelineTrack)?.chainEffectEntries?.values?.firstOrNull { it.clipId == clipId }
    val clipName = entry?.name ?: "Chain Effect"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(Theme[colors][background]),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme[colors][background])
                .border(1.dp, themeBorder)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = trackName,
                    style = Theme[typography][small],
                    color = Theme[colors][foreground],
                )
                Text(
                    text = "•",
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
                Text(
                    text = clipName,
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
            }

            Button(
                onClick = viewModel::closeChainEffect,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Icon,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Chain Effect",
                    tint = Theme[colors][mutedForeground],
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Box(modifier = Modifier.height(280.dp).fillMaxWidth()) {
            ScrollArea(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(DefaultShape)
                    .padding(bottom = 10.dp),
                orientation = ScrollBarOrientation.Horizontal,
                state = scrollState,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimelineChainEffectSourceSlot(
                        source = runtime.source,
                        onAddSource = { sourcePickerVisible = true },
                        onRemoveSource = { viewModel.setChainEffectSource(trackIndex, clipId, null) },
                    )

                    Spacer(Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(themeBorder),
                    )

                    DragAndDropContainer(state = dragAndDropState) {
                        ChainView(
                            chain = runtime.processors,
                            dragAndDropState = dragAndDropState,
                            modifier = Modifier.fillMaxHeight(),
                            showContextMenu = true,
                            showRemoteFocus = false,
                            privateTimelineChain = true,
                            onAddDevice = { device, index ->
                                viewModel.addChainEffectProcessor(trackIndex, clipId, device, index)
                            },
                            onMoveDevice = { fromIndex, toIndex ->
                                viewModel.reorderChainEffectProcessor(trackIndex, clipId, fromIndex, toIndex)
                            },
                        )
                    }
                }
            }
        }

        ChainDevicePicker(
            visible = sourcePickerVisible,
            sampling = false,
            isDeviceTypeEnabled = { type ->
                type == PianoRollChainDevice::class ||
                    type == KeyframesChainDevice::class ||
                    type == CompositionChainDevice::class
            },
            onPickComponent = { device ->
                viewModel.setChainEffectSource(trackIndex, clipId, device)
                sourcePickerVisible = false
            },
            onDismiss = { sourcePickerVisible = false },
        )
    }
}

@Composable
private fun TimelineChainEffectSourceSlot(
    source: GenericChainDevice<*>?,
    onAddSource: () -> Unit,
    onRemoveSource: () -> Unit,
) {
    val themeBorder = Theme[colors][border]
    key(source) {
        if (source == null) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .clip(DefaultShape)
                    .background(Theme[colors][background])
                    .border(1.dp, themeBorder, DefaultShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onAddSource,
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Icon,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Choose timeline source",
                            tint = Theme[colors][foreground],
                        )
                    }
                    Text(
                        text = "Add Source",
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight(),
                ) {
                    source.Content()
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onRemoveSource,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Icon,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove source",
                        tint = Theme[colors][mutedForeground],
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
