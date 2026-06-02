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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.ScaleToFit
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.selectionBorder
import dev.anthonyhfm.amethyst.ui.theme.selectionSurface

import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.SavableViewportLaunchpad.MidiFighter64.MidiFighter64Style

class ViewportMidiFighter64(
    override var shape: Shape = RoundedCornerShape(4),
    override var size: Size = Size(8f, 8f),
    val interactive: Boolean = true,
    initialStyle: MidiFighter64Style = MidiFighter64Style.Black,
) : LaunchpadViewportElement() {
    override val name: String = "Midi Fighter 64"

    override val layout: LaunchpadLayout = LaunchpadLayout.LAYOUT_8X8

    var style by mutableStateOf(initialStyle)

    override val hasStyleOptions: Boolean = true

    override fun applyNetworkStyle(key: String) {
        val styleValue = MidiFighter64Style.entries.firstOrNull { it.name == key } ?: return
        style = styleValue
    }

    @Composable
    override fun StyleConfigContent(onDismiss: () -> Unit) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val styles = MidiFighter64Style.entries

                styles.forEach { styleOption ->
                    val isSelected = style == styleOption
                    val borderColor = if (isSelected) {
                        Theme[colors][selectionBorder]
                    } else {
                        Theme[colors][border]
                    }

                    val cardBackground = if (isSelected) {
                        Theme[colors][selectionSurface].copy(alpha = 0.15f)
                    } else {
                        Theme[colors][background]
                    }

                    val previewDevice = remember(styleOption) {
                        ViewportMidiFighter64(interactive = false, initialStyle = styleOption)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(240.dp)
                            .border(2.dp, borderColor, DefaultShape)
                            .background(cardBackground, DefaultShape)
                            .clickable {
                                style = styleOption
                                dev.anthonyhfm.amethyst.core.network.sync.DeviceSyncCoordinator.onDeviceStyleChanged(this@ViewportMidiFighter64, styleOption.name)
                            }
                            .padding(16.dp),

                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            ScaleToFit {
                                previewDevice.Content()
                            }
                        }

                        Text(
                            text = if (styleOption == MidiFighter64Style.Black) "Black Variant" else "White Variant",
                            style = Theme[typography][small],
                            color = Theme[colors][foreground],
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Content() {
        val previewGrid by previewState.grid
        val density = LocalDensity.current

        var deviceBitmap: ImageBitmap? by remember { mutableStateOf(null) }

        LaunchedEffect(style) {
            val fileName = if (style == MidiFighter64Style.White) {
                "files/devices/MF64/MIDI_Fighter_64_White_ml.png"
            } else {
                "files/devices/MF64/MIDI_Fighter_64_Black_ml.png"
            }
            
            deviceBitmap = Res.readBytes(fileName).decodeToImageBitmap()
        }

        if (deviceBitmap != null) {
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
                            color = Color(0xFF202020),
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
                            image = deviceBitmap!!,
                            srcOffset = IntOffset(109, 109),
                            srcSize = IntSize(582, 582),
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
