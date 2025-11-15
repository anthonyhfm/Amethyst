package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ControlPointDuplicate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.dropDownMenu

@Composable
fun FramePreviewButton(
    index: Int,
    selected: Boolean,
    timing: Timing,
    onEvent: (KeyframesChainDeviceContract.Event) -> Unit,
    parent: dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice? = null
) {
    val density = LocalDensity.current.density
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
    
    var showRightClickMenu by remember { mutableStateOf(false) }
    var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    val totalFrames = parent?.state?.value?.frames?.size ?: 1
    val isFirstFrame = index == 0
    val isLastFrame = index == totalFrames - 1

    // Context menu
    Dropdown(
        isOpen = showRightClickMenu,
        menu = dropDownMenu {
            item("addKeyframe", "Add Keyframe") {
                icon(Icons.Default.Add)
            }

            item("duplicate", "Duplicate") {
                icon(Icons.Default.ControlPointDuplicate)
            }

            item("copy", "Copy") {
                icon(Icons.Default.ContentCopy)
            }

            if (hasFramesInClipboard) {
                item("paste", "Paste") {
                    icon(Icons.Default.ContentCopy)
                }
            }

            horizontalDivider()

            item("delete", "Delete") {
                icon(Icons.Default.Delete)
            }
        },
        offset = rightClickMenuOffset,
        onItemSelected = {
            when (it) {
                "duplicate" -> {
                    onEvent(KeyframesChainDeviceContract.Event.OnDuplicateFrame(index))
                }
                "delete" -> {
                    if (totalFrames > 1) {
                        onEvent(KeyframesChainDeviceContract.Event.OnDeleteFrame(index))
                    }
                }
                "addKeyframe" -> {
                    onEvent(KeyframesChainDeviceContract.Event.OnAddFrame(index + 1))
                }
                "copy" -> {
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
                "paste" -> {
                    if (hasFramesInClipboard && parent != null) {
                        val framesToPaste = (clipboard as ClipboardData.Keyframe).frames
                        parent.pasteFrames(framesToPaste, index + 1)
                    }
                }
            }
            showRightClickMenu = false
        },
        onDismiss = {
            showRightClickMenu = false
        }
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .fillMaxWidth()
            .background(
                when {
                    selected && isSelectedInManager -> MaterialTheme.colorScheme.tertiary
                    isSelectedInManager -> MaterialTheme.colorScheme.tertiary.copy(0.5f)
                    selected -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.surfaceContainer
                }
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
            .rightClickable {
                rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
                showRightClickMenu = true
            }
            .padding(4.dp),

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = when (timing) {
                is Timing.Duration -> {
                    "${timing.duration.inWholeMilliseconds} ms"
                }

                is Timing.Rythm -> {
                    timing.timing.text
                }
            },
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .width(56.dp)
                .background(
                    when {
                        selected && isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                        isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                        selected -> MaterialTheme.colorScheme.onTertiary
                        else -> MaterialTheme.colorScheme.onTertiary
                    }
                )
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            fontWeight = FontWeight.Bold,
            color = when {
                selected && isSelectedInManager -> MaterialTheme.colorScheme.tertiary
                isSelectedInManager -> MaterialTheme.colorScheme.tertiary
                selected -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.tertiary
            },
        )

        Text(
            text = "Frame ${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize,
            color = when {
                selected && isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                isSelectedInManager -> MaterialTheme.colorScheme.onTertiary
                selected -> MaterialTheme.colorScheme.onTertiary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
