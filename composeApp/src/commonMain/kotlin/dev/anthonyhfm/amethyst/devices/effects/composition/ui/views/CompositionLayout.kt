package dev.anthonyhfm.amethyst.devices.effects.composition.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionChainDevice
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionGraphEditor
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.GraphSplitHandle
import dev.anthonyhfm.amethyst.devices.effects.composition.ui.components.PlaybackOptions
import dev.anthonyhfm.amethyst.workspace.WorkspaceViewModel
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport

@Composable
fun CompositionLayout(
    device: CompositionChainDevice,
    editor: CompositionGraphEditor,
    modifier: Modifier = Modifier,
) {
    val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }
    val deviceState by device.state.collectAsState()
    val splitRatio = deviceState.splitRatio
    val handleWidthPx = with(LocalDensity.current) { 12.dp.toPx() }

    var totalWidthPx by remember { mutableStateOf(0f) }

    Row(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .onSizeChanged { totalWidthPx = it.width.toFloat() },
    ) {
        Column(
            modifier = Modifier
                .weight(splitRatio.coerceIn(0.05f, 0.95f)),

            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkspaceViewport(
                modifier = Modifier
                    .weight(1f),
                viewportKey = "workspace-keyframes",
                config = ViewportConfig(
                    minZoom = 0.5f,
                    maxZoom = 2f,
                    enablePanning = true,
                    enableZoom = true,
                    draggableObjects = false,
                    panBoundsPolicy = ViewportPanBoundsPolicy.ClampToContent(
                        allowedOutOfBoundsFraction = 0.5f,
                    ),
                    showGrid = false,
                    showOrigin = false,
                    showActions = false,
                    showRemoteCursors = true,
                    contentPadding = 48.dp
                ),
                onEvent = { viewModel.onEvent(it) }
            )

            PlaybackOptions(device = device)
        }

        GraphSplitHandle(
            onDragByPx = { deltaX ->
                if (totalWidthPx > 0f) {
                    val paneWidthPx = (totalWidthPx - handleWidthPx).coerceAtLeast(1f)
                    device.updateSplitRatio(splitRatio + deltaX / paneWidthPx)
                }
            },
        )

        Box(
            modifier = Modifier
                .weight((1f - splitRatio).coerceIn(0.05f, 0.95f))
        ) {
            GraphViewport(device = device, editor = editor)
        }
    }
}
