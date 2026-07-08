package dev.anthonyhfm.amethyst.ui.launchpad.viewport

import amethyst.composeapp.generated.resources.Res
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.settings.data.GeneralSettings
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadSurfaceDetectionOverlay
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadButton
import dev.anthonyhfm.amethyst.ui.launchpad.components.GenericLaunchpadLayout
import dev.anthonyhfm.amethyst.ui.launchpad.components.LaunchpadLayout
import dev.anthonyhfm.amethyst.workspace.help.decodeImageBytes
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.floor
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.readResourceBytes
import kotlin.math.pow

import dev.anthonyhfm.amethyst.ui.launchpad.LaunchpadGraphicsRepository

class ViewportLaunchpadPro(
    override var shape: Shape = RoundedCornerShape(1),
    override var size: Size = Size(10f, 10f),
    val interactive: Boolean = true
) : LaunchpadViewportElement() {
    override val name: String = "Launchpad Pro"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_10X10

    @OptIn(InternalResourceApi::class)
    @Composable
    override fun Content() {
        val previewGrid by previewState.grid
        val density = LocalDensity.current

        val graphicsState by LaunchpadGraphicsRepository.graphicsState.collectAsState()
        val lppGraphics = graphicsState.lpp

        if (lppGraphics != null) {
            val buttonsBitmap = lppGraphics.buttons
            val deviceBitmap = lppGraphics.device
            val ledspotsBitmap = lppGraphics.ledspots
            Box(
                modifier = Modifier
                    .requiredSize(width = size.width.dp * 40, height = size.height.dp * 40)
                    .clip(shape),
            ) {
                val padding = 12f * density.density

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
                                            width = size.width / 10,
                                            height = size.height / 10
                                        ),
                                    )
                                }
                            }
                        }

                        if (previewGrid[99].color != Color.Black) {
                            drawRect(
                                color = previewGrid[99].color.let {
                                    it.copy(
                                        red = it.red.pow(0.3f),
                                        green = it.green.pow(0.3f),
                                        blue = it.blue.pow(0.3f),
                                    )
                                },
                                topLeft = Offset(
                                    x = size.width / 2 - padding,
                                    y = size.height - padding - (2f * density.density)
                                ),
                                size = size.copy(
                                    width = 24f * density.density,
                                    height = size.height / 10
                                ),
                            )
                        }

                        drawImage(
                            image = buttonsBitmap!!,
                            srcOffset = IntOffset(40, 40),
                            srcSize = IntSize(945, 945),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            blendMode = BlendMode.Multiply
                        )

                        drawImage(
                            image = ledspotsBitmap!!,
                            srcOffset = IntOffset(40, 40),
                            srcSize = IntSize(945, 945),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )

                        drawImage(
                            image = deviceBitmap!!,
                            srcOffset = IntOffset(40, 40),
                            srcSize = IntSize(945, 945),
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

                            val setupButtonTopLeftX = layoutSize.width / 2f - 24f
                            val setupButtonTopLeftY = layoutSize.height.toFloat() - 24f
                            val setupButtonWidth = 48f
                            val setupButtonHeight = layoutSize.height / 10f

                            if (offset.x >= setupButtonTopLeftX && offset.x <= setupButtonTopLeftX + setupButtonWidth &&
                                offset.y >= setupButtonTopLeftY && offset.y <= setupButtonTopLeftY + setupButtonHeight) {
                                Pair(9, 9)
                            } else if (offset.x >= padding && offset.x <= layoutSize.width - padding &&
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