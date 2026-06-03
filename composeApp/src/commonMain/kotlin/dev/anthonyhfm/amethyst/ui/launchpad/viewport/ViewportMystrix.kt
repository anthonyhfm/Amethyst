package dev.anthonyhfm.amethyst.ui.launchpad.viewport

import amethyst.composeapp.generated.resources.Res
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadSurfaceDetectionOverlay
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.floor
import kotlin.math.pow

class ViewportMystrix(
    override var shape: Shape = RoundedCornerShape(1),
    override var size: Size = Size(8f, 8f),
    val interactive: Boolean = true
) : LaunchpadViewportElement() {
    override val name: String = "Mystrix"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_8X8

    @Composable
    override fun Content() {
        val previewGrid by previewState.grid
        val density = LocalDensity.current

        var buttonsBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        var deviceBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        var ledspotsBitmap: ImageBitmap? by remember { mutableStateOf(null) }

        LaunchedEffect(Unit) {
            buttonsBitmap = Res.readBytes("files/launchpad/Mystrix/Mystrix_Buttons_Layer_ml.png").decodeToImageBitmap()
            deviceBitmap = Res.readBytes("files/launchpad/Mystrix/Mystrix_Device_Layer_ml.png").decodeToImageBitmap()
            ledspotsBitmap = Res.readBytes("files/launchpad/Mystrix/Mystrix_Spots_Layer_ml.png").decodeToImageBitmap()
        }

        if (buttonsBitmap != null && deviceBitmap != null && ledspotsBitmap != null) {
            Box(
                modifier = Modifier
                    .requiredSize(width = size.width.dp * 40, height = size.height.dp * 40)
                    .clip(shape),
            ) {
                val padding = 8f * density.density

                val launchpadCanvas: @Composable () -> Unit = {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        drawRect(
                            color = Color(0xFF303030),
                            topLeft = Offset(
                                x = padding,
                                y = padding
                            ),
                            size = size.copy(
                                width = size.width - (padding * 2),
                                height = size.height - (padding * 2)
                            ),
                        )

                        for (x in 1..8) {
                            for (y in 1..8) {
                                if (previewGrid[x + (y * 10)].color != Color.Black) {
                                    drawRect(
                                        color = previewGrid[x + (y * 10)].color.let {
                                            it.copy(
                                                red = it.red.pow(0.3f),
                                                green = it.green.pow(0.3f),
                                                blue = it.blue.pow(0.3f),
                                            )
                                        },
                                        topLeft = Offset(
                                            x = padding + ((x - 1) * ((size.width - (padding * 2)) / 8)),
                                            y = padding + ((8 - y) * ((size.height - (padding * 2)) / 8))
                                        ),
                                        size = size.copy(
                                            width = (size.width - padding * 2) / 8,
                                            height = (size.height - padding * 2) / 8
                                        ),
                                    )
                                }
                            }
                        }

                        drawImage(
                            image = buttonsBitmap!!,
                            srcOffset = IntOffset(138, 139),
                            srcSize = IntSize(743, 743),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            blendMode = BlendMode.Multiply
                        )

                        drawImage(
                            image = ledspotsBitmap!!,
                            srcOffset = IntOffset(138, 139),
                            srcSize = IntSize(743, 743),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )

                        drawImage(
                            image = deviceBitmap!!,
                            srcOffset = IntOffset(138, 139),
                            srcSize = IntSize(743, 743),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )
                    }
                }

                if (interactive) {
                    LaunchpadSurfaceDetectionOverlay(
                        layoutType = layout,
                        onPadDragStart = { x, y -> handlePadDragStart(x, y) },
                        onPadDrag = { x, y -> handlePadDrag(x, y) },
                        onPadDragEnd = { handlePadDragEnd() },
                        calculatePad = { offset, layoutSize ->
                            val contentWidth = layoutSize.width.toFloat() - (padding * 2)
                            val contentHeight = layoutSize.height.toFloat() - (padding * 2)

                            val colWidth = contentWidth / 8f
                            val rowHeight = contentHeight / 8f

                            if (offset.x >= padding && offset.x <= layoutSize.width - padding &&
                                offset.y >= padding && offset.y <= layoutSize.height - padding) {
                                val visualX = floor((offset.x - padding) / colWidth).toInt().coerceIn(0, 7)
                                val visualY = floor((offset.y - padding) / rowHeight).toInt().coerceIn(0, 7)
                                Pair(visualX, 7 - visualY)
                            } else {
                                null
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        launchpadCanvas()
                    }
                } else {
                    launchpadCanvas()
                }
            }
        }
    }
}
