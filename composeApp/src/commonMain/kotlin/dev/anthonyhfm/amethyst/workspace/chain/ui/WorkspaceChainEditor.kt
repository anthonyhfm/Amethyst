package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropContainer
import com.mohamedrejeb.compose.dnd.rememberDragAndDropState
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollAreaState
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollBarOrientation
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode

@Composable
fun WorkspaceChainEditor(
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
    ) {
        ScrollArea(
            modifier = Modifier
                .clip(DefaultShape)
                .height(280.dp)
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            orientation = ScrollBarOrientation.Horizontal,
            state = scrollState,
        ) {
            Row(
                modifier = Modifier.padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
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
                    )
                }
            }
        }
    }
}

