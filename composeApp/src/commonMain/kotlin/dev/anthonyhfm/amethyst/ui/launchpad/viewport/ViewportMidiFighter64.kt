package dev.anthonyhfm.amethyst.ui.launchpad.viewport

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
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
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadSurfaceDetectionOverlay
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

class ViewportMidiFighter64(
    override var shape: Shape = RoundedCornerShape(8),
    override var size: Size = Size(8f, 8f),
    val interactive: Boolean = true,
) : LaunchpadViewportElement() {
    override val name: String = "Midi Fighter 64"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_8X8

    @Composable
    override fun Content() {
        val previewGrid by previewState.grid

        Box(
            modifier = Modifier
                .requiredSize(width = size.width.dp * 40, height = size.height.dp * 40)
                .clip(shape)
                .background(Color(0xFF0d0d0d)),
            contentAlignment = Alignment.Center
        ) {
            if (interactive) {
                LaunchpadSurfaceDetectionOverlay(
                    layoutType = layout,
                    onPadDragStart = { x, y ->
                        handlePadDragStart(x, y)
                    },
                    onPadDrag = { x, y ->
                        handlePadDrag(x, y)
                    },
                    onPadDragEnd = {
                        handlePadDragEnd()
                    },
                    modifier = Modifier.fillMaxSize(0.94f)
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
                    modifier = Modifier.fillMaxSize(0.94f)
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
    effectData: RawLEDUpdate,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GenericLaunchpadButton(
            sizeModifier = Modifier
                .fillMaxSize(0.8f),
            effect = effectData,
            enableLightSpot = false,
            shape = CircleShape
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxSize(0.62f)
                .background(Color(0xFF0A0A0A))
        )
    }
}