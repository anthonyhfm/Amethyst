package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import com.composables.icons.lucide.BookOpenText
import com.composables.icons.lucide.ClipboardPaste
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.CopyPlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Replace
import com.composables.icons.lucide.Trash2
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.drag.DraggableItem
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.ModifierKeysState
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardData
import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import dev.anthonyhfm.amethyst.core.controls.undo.UndoableAction
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuItemVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ContextMenuSeparator
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollAreaState
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollBarOrientation
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainCanvas
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.help.GetHelpWorkspaceMode

@Composable
fun MobileWorkspaceChainEditor(
    devices: List<GenericChainDevice<*>>,
    scrollState: ScrollAreaState = rememberScrollAreaState(),
    modifier: Modifier = Modifier,
) {
    val dragAndDropState = rememberDragAndDropState<GenericChainDevice<*>>()
    val currentMode by WorkspaceRepository.mode.collectAsState()
    val chain = when (currentMode) {
        is SamplingChainWorkspaceMode -> WorkspaceRepository.samplingChain
        else -> WorkspaceRepository.lightsChain
    }

    Column(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {
                    SelectionManager.clear()
                }
            ),

        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ScrollArea(
            modifier = Modifier
                .height(280.dp)
                .fillMaxWidth()
                .background(Theme[chainColorTokens][chainCanvas])
                .padding(bottom = 16.dp),
            orientation = ScrollBarOrientation.Horizontal,
            state = scrollState,
            scrollBarThickness = 16.dp,
        ) {
            Row(
                modifier = Modifier.padding(top = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DragAndDropContainer(
                    state = dragAndDropState,
                ) {
                    ChainView(
                        chain = chain,
                        dragAndDropState = dragAndDropState,
                        showContextMenu = true,
                        showRemoteFocus = true,
                        dragAfterLongPress = true,
                    )
                }
            }
        }
    }
}
