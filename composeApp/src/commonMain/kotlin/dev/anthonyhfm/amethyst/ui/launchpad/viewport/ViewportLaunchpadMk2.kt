package dev.anthonyhfm.amethyst.ui.launchpad.viewport

import amethyst.composeapp.generated.resources.Res
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
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
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlin.math.floor
import kotlin.math.pow

class ViewportLaunchpadMk2(
    override var shape: Shape = RoundedCornerShape(1.5f),
    override var size: Size = Size(9f, 9f),
    val interactive: Boolean = true,
) : LaunchpadViewportElement() {
    override val name: String = "Launchpad MK2"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_9X9

    @Composable
    override fun Content() {
        val previewGrid by previewState.grid
        val density = LocalDensity.current
        val useSimplified: Boolean by GeneralSettings.simplifiedGraphics.flow.collectAsState()

        var buttonsBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        var deviceBitmap: ImageBitmap? by remember { mutableStateOf(null) }
        var ledspotsBitmap: ImageBitmap? by remember { mutableStateOf(null) }

        LaunchedEffect(useSimplified) {
            val suffix = if (useSimplified) "anth" else "ml"

            try {
                buttonsBitmap = Res.readBytes("files/launchpad/LP2/LP2_Buttons_Layer_$suffix.png").decodeToImageBitmap()
                deviceBitmap = Res.readBytes("files/launchpad/LP2/LP2_Device_Layer_$suffix.png").decodeToImageBitmap()
                ledspotsBitmap = Res.readBytes("files/launchpad/LP2/LP2_Spots_Layer_$suffix.png").decodeToImageBitmap()
            } catch (ex: Exception) { // open source fallback
                buttonsBitmap = Res.readBytes("files/launchpad/LP2/LP2_Buttons_Layer_anth.png").decodeToImageBitmap()
                deviceBitmap = Res.readBytes("files/launchpad/LP2/LP2_Device_Layer_anth.png").decodeToImageBitmap()
                ledspotsBitmap = Res.readBytes("files/launchpad/LP2/LP2_Spots_Layer_anth.png").decodeToImageBitmap()
            }
        }

        if (buttonsBitmap != null && deviceBitmap != null && ledspotsBitmap != null) {
            Box(
                modifier = Modifier
                    .requiredSize(width = size.width.dp * 40, height = size.height.dp * 40)
                    .clip(shape),
            ) {
                val padding = 16f * density.density

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
                                width = size.width + padding * 2,
                                height = size.height + padding * 2
                            ),
                        )

                        for (x in 1..9) {
                            for (y in 1..9) {
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
                                            x = (x - 1) * ((size.width - (padding * 2)) / 9) + padding,
                                            y = (9 - y) * ((size.height - (padding * 2)) / 9) + padding
                                        ),
                                        size = size.copy(
                                            width = size.width / 10,
                                            height = size.height / 10
                                        ),
                                    )
                                }
                            }
                        }

                        drawImage(
                            image = buttonsBitmap!!,
                            srcOffset = IntOffset(121, 34),
                            srcSize = IntSize(870, 870),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            blendMode = BlendMode.Multiply
                        )

                        drawImage(
                            image = ledspotsBitmap!!,
                            srcOffset = IntOffset(121, 34),
                            srcSize = IntSize(870, 870),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        )

                        drawImage(
                            image = deviceBitmap!!,
                            srcOffset = IntOffset(121, 34),
                            srcSize = IntSize(870, 870),
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
                            val paddingPx = 16f * density.density
                            val contentWidth = layoutSize.width.toFloat() - (paddingPx * 2)
                            val contentHeight = layoutSize.height.toFloat() - (paddingPx * 2)

                            val colWidth = contentWidth / 9f
                            val rowHeight = contentHeight / 9f

                            if (offset.x >= paddingPx && offset.x <= layoutSize.width - paddingPx &&
                                offset.y >= paddingPx && offset.y <= layoutSize.height - paddingPx) {
                                val visualX = floor((offset.x - paddingPx) / colWidth).toInt().coerceIn(0, 8)
                                val visualY = floor((offset.y - paddingPx) / rowHeight).toInt().coerceIn(0, 8)

                                Pair(visualX, 8 - visualY)
                            } else {
                                null
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
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