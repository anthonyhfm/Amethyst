package dev.anthonyhfm.amethyst.ui.launchpad.viewport

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadSurfaceDetectionOverlay
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

class ViewportMystrix(
    override var shape: Shape = RoundedCornerShape(2),
    override var size: Size = Size(8f, 8f),
    val interactive: Boolean = true
) : LaunchpadViewportElement() {
    override val name: String = "Mystrix"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_8X8

    override val content: @Composable (() -> Unit) = {
        val previewGrid by previewState.grid

        Box(
            modifier = Modifier
                .size(width = size.width.dp * 40, height = size.height.dp * 40)
                .clip(shape)
                .background(Color(0xFF0d0d0d)),
            contentAlignment = Alignment.Center
        ) {
            if (interactive) {
                LaunchpadSurfaceDetectionOverlay(
                    layoutType = layout,
                    onPadPressed = { x, y ->
                        onEvent?.invoke(WorkspaceContract.Event.OnPressVirtualDevice(x, y, position.value))
                    },
                    onPadReleased = { x, y ->
                        onEvent?.invoke(WorkspaceContract.Event.OnReleaseVirtualDevice(x, y, position.value))
                    },
                    modifier = Modifier.fillMaxSize(0.92f)
                ) {
                    GenericLaunchpadLayout(
                        layoutType = layout,
                        modifier = Modifier.fillMaxSize()
                    ) { x, y ->
                        GridPad(
                            x = x,
                            y = y,
                            effectData = previewGrid[x + y * 10],
                            modifier = Modifier
                        )
                    }
                }
            } else {
                GenericLaunchpadLayout(
                    layoutType = layout,
                    modifier = Modifier.fillMaxSize(0.92f)
                ) { x, y ->
                    GridPad(
                        x = x,
                        y = y,
                        effectData = previewGrid[x + y * 10],
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridPad(
    x: Int,
    y: Int,
    effectData: RawUpdate,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (x in 2..5 && y in 2..5) {
            ClippedPad(
                topLeft = x == 2 && y == 5,
                topRight = x == 5 && y == 5,
                bottomLeft = x == 2 && y == 2,
                bottomRight = x == 5 && y == 2,
                effectData = effectData
            )
        } else {
            GenericLaunchpadButton(
                sizeModifier = Modifier.fillMaxSize(0.84f),
                effect = effectData,
                shape = RoundedCornerShape(6)
            )
        }
    }
}

@Composable
private fun ClippedPad(
    topLeft: Boolean,
    topRight: Boolean,
    bottomLeft: Boolean,
    bottomRight: Boolean,
    effectData: RawUpdate,
) {
    GenericLaunchpadButton(
        sizeModifier = Modifier
            .clip(
                CutCornerShape(
                    bottomEndPercent = if (topLeft) 30 else 0,
                    bottomStartPercent = if (topRight) 30 else 0,
                    topEndPercent = if (bottomLeft) 30 else 0,
                    topStartPercent = if (bottomRight) 30 else 0,
                )
            )
            .fillMaxSize(0.84f),
        effect = effectData,
        shape = RoundedCornerShape(6)
    )
}