package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.GripVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItemScope
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenu
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItem
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionForeground
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun ReorderableItemScope.FramePreviewButton(
    index: Int,
    selected: Boolean,
    timing: Timing,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit,
    parent: dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice? = null,
) {
    val selections by SelectionManager.selections.collectAsState()
    val isSelectedInManager = parent?.let { parentDevice ->
        selections.any {
            it is Selectable.KeyframeItem &&
                    it.parent == parentDevice &&
                    it.frameIndex == index
        }
    } ?: false

    val clipboard by ClipboardManager.clipboardData.collectAsState()
    val hasFramesInClipboard = clipboard is ClipboardData.Keyframe

    val totalFrames = parent?.state?.value?.frames?.size ?: 1
    val isHighlighted = selected || isSelectedInManager

    ContextMenu(
        modifier = Modifier.fillMaxWidth(),
        trigger = {
            Row(
                modifier = Modifier
                    .clip(DefaultShape)
                    .fillMaxWidth()
                    .background(
                        when {
                            selected && isSelectedInManager -> Theme[colors][selectionSurface]
                            isSelectedInManager -> Theme[colors][selectionSurface].copy(alpha = 0.5f)
                            selected -> Theme[colors][selectionSurface]
                            else -> Theme[colors][card]
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            !(selected && isSelectedInManager) -> Theme[colors][selectionSurface].copy(alpha = 0.1f)

                            else -> Color.Transparent
                        },
                        shape = DefaultShape,
                    )
                    .clickable {
                        onEvent(
                            KeyframesChainDeviceContract.Event.OnSelectFrame(
                                frameIndex = index,
                                rangeSelect = ModifierKeysState.isShiftPressed,
                                multiSelect = ModifierKeysState.isCtrlPressed
                            )
                        )
                    }
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Lucide.GripVertical,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(12.dp)
                        .dragAnchor(),
                    tint = if (isHighlighted) Theme[colors][selectionForeground].copy(alpha = 0.6f)
                           else Theme[colors][mutedForeground],
                )

                Text(
                    text = when (timing) {
                        is Timing.Duration -> "${timing.duration.inWholeMilliseconds} ms"
                        is Timing.Rythm -> timing.timing.text
                    },
                    modifier = Modifier
                        .clip(SmallShape)
                        .width(56.dp)
                        .background(
                            if (isHighlighted) Theme[colors][selectionForeground].copy(alpha = 0.15f)
                            else Theme[colors][card]
                        )
                        .padding(vertical = 2.dp),
                    style = Theme[typography][small],
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted) Theme[colors][selectionForeground]
                            else Theme[colors][foreground],
                )

                Text(
                    text = "Frame ${index + 1}",
                    style = Theme[typography][small],
                    color = if (isHighlighted) Theme[colors][selectionForeground]
                            else Theme[colors][foreground],
                )
            }
        }
    ) {
        ContextMenuItem(
            onClick = { onEvent(KeyframesChainDeviceContract.Event.OnAddFrame(index + 1)) }
        ) {
            Icon(Lucide.Plus, null, modifier = Modifier.size(16.dp))
            Text("Add Keyframe", modifier = Modifier.weight(1f))
        }

        ContextMenuItem(
            onClick = { onEvent(KeyframesChainDeviceContract.Event.OnDuplicateFrame(index)) }
        ) {
            Icon(Lucide.CopyPlus, null, modifier = Modifier.size(16.dp))
            Text("Duplicate", modifier = Modifier.weight(1f))
        }

        ContextMenuItem(
            onClick = {
                if (parent != null) {
                    val framesToCopy = if (isSelectedInManager) {
                        val selectedKeyframes = selections.filterIsInstance<Selectable.KeyframeItem>()
                            .filter { it.parent == parent }
                        selectedKeyframes.map { parent.state.value.frames[it.frameIndex] }
                    } else {
                        listOf(parent.state.value.frames[index])
                    }
                    ClipboardManager.setClipboardData(ClipboardData.Keyframe(framesToCopy))
                }
            }
        ) {
            Icon(Lucide.Copy, null, modifier = Modifier.size(16.dp))
            Text("Copy", modifier = Modifier.weight(1f))
        }

        if (hasFramesInClipboard) {
            ContextMenuItem(
                onClick = {
                    if (parent != null) {
                        val framesToPaste = (clipboard as ClipboardData.Keyframe).frames
                        parent.pasteFrames(framesToPaste, index + 1)
                    }
                }
            ) {
                Icon(Lucide.ClipboardPaste, null, modifier = Modifier.size(16.dp))
                Text("Paste", modifier = Modifier.weight(1f))
            }
        }

        ContextMenuSeparator()

        ContextMenuItem(
            enabled = totalFrames > 1,
            onClick = {
                if (totalFrames > 1) {
                    onEvent(KeyframesChainDeviceContract.Event.OnDeleteFrame(index))
                }
            }
        ) {
            Icon(Lucide.Trash2, null, modifier = Modifier.size(16.dp))
            Text("Delete", modifier = Modifier.weight(1f))
        }
    }
}
