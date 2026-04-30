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

class ViewportLaunchpadMk2(
    override var shape: Shape = RoundedCornerShape(4),
    override var size: Size = Size(9f, 9f),
    val interactive: Boolean = true,
) : LaunchpadViewportElement() {
    override val name: String = "Launchpad MK2"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_9X9

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
    effectData: RawLEDUpdate,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (y == 9 && x > 0 && x < 9) {
            EdgePad(effectData = effectData)
        } else if (x == 9 && y > 0 && y < 9) {
            EdgePad(effectData = effectData)
        } else if (x in 1..8 && y in 1..8) {
            GenericLaunchpadButton(
                sizeModifier = Modifier.fillMaxSize(0.82f),
                effect = effectData
            )
        }
    }
}

@Composable
private fun EdgePad(effectData: RawLEDUpdate) {
    Box(
        modifier = Modifier.fillMaxSize(0.8f),
        contentAlignment = Alignment.Center
    ) {
        GenericLaunchpadButton(
            sizeModifier = Modifier.fillMaxSize(0.8f),
            enableLightSpot = false,
            shape = CircleShape,
            effect = effectData
        )

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .fillMaxSize(0.65f)
                .background(Color(0xFF0A0A0A))
        )
    }
}
