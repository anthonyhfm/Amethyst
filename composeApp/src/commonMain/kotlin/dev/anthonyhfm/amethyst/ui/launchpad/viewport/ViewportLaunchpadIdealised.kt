package dev.anthonyhfm.amethyst.ui.launchpad.viewport

import amethyst.composeapp.generated.resources.Res
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadSurfaceDetectionOverlay
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.floor
import org.jetbrains.compose.resources.InternalResourceApi
import kotlin.math.pow

class ViewportLaunchpadIdealised(
    override var shape: Shape = RoundedCornerShape(1),
    override var size: Size = Size(10f, 10f),
    val interactive: Boolean = true
) : LaunchpadViewportElement() {
    override val name: String = "Idealised"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_10X10

    @OptIn(InternalResourceApi::class)
    @Composable
    override fun Content() {
        val previewGrid by previewState.grid
        val density = LocalDensity.current

        var buttonsBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        var deviceBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        var ledspotsBitmap: ImageBitmap? by remember { mutableStateOf(null) }

        LaunchedEffect(Unit) {
            buttonsBitmap = Res.readBytes("files/launchpad/Idealised/Idealised_Buttons_Layer_ml.png").decodeToImageBitmap()
            deviceBitmap = Res.readBytes("files/launchpad/Idealised/Idealised_Device_Layer_ml.png").decodeToImageBitmap()
            ledspotsBitmap = Res.readBytes("files/launchpad/Idealised/Idealised_Spots_Layer_ml.png").decodeToImageBitmap()
        }

        if (buttonsBitmap != null && deviceBitmap != null && ledspotsBitmap != null) {
            Box(
                modifier = Modifier
                    .requiredSize(width = size.width.dp * 40, height = size.height.dp * 40)
                    .clip(shape),
            ) {
                val padding = 18f * density.density

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

                        for (x in 0..9) {
                            for (y in 0..9) {
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
                                            x = padding + (x * ((size.width - (padding * 2)) / 10)),
                                            y = padding + ((9 - y) * ((size.height - (padding * 2)) / 10))
                                        ),
                                        size = size.copy(
                                            width = (size.width - padding * 2) / 10,
                                            height = (size.height - padding * 2) / 10
                                        ),
                                    )
                                }
                            }
                        }

                        drawImage(
                            image = buttonsBitmap!!,
                            srcOffset = IntOffset(24, 23),
                            srcSize = IntSize(973, 973),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            blendMode = BlendMode.Multiply
                        )

                        drawImage(
                            image = ledspotsBitmap!!,
                            srcOffset = IntOffset(24, 23),
                            srcSize = IntSize(973, 973),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )

                        drawImage(
                            image = deviceBitmap!!,
                            srcOffset = IntOffset(24, 23),
                            srcSize = IntSize(973, 973),
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

                            val colWidth = contentWidth / 10f
                            val rowHeight = contentHeight / 10f

                            if (offset.x >= padding && offset.x <= layoutSize.width - padding &&
                                offset.y >= padding && offset.y <= layoutSize.height - padding) {
                                val col = floor((offset.x - padding) / colWidth).toInt().coerceIn(0, 9)
                                val row = (9 - floor((offset.y - padding) / rowHeight).toInt()).coerceIn(0, 9)
                                Pair(col, row)
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