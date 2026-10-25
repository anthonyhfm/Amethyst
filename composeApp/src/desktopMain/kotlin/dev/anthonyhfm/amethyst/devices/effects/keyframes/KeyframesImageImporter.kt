package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.KeyframesEntry
import java.awt.AlphaComposite
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.w3c.dom.Node
import kotlin.math.floor
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

internal object KeyframesImageImporter {
    data class Target(
        val launchpadId: String,
        val position: IntOffset,
        val size: IntSize,
    )

    fun load(
        data: ByteArray,
        bounds: Pair<IntOffset, IntSize>,
        targets: List<Target>,
    ): List<Frame> {
        require(targets.isNotEmpty() && bounds.second.width > 0 && bounds.second.height > 0) {
            "Add a Launchpad to the workspace before importing."
        }

        ImageIO.createImageInputStream(ByteArrayInputStream(data)).use { input ->
            requireNotNull(input) { "The image could not be read." }

            val readers = ImageIO.getImageReaders(input)

            require(readers.hasNext()) { "Choose a valid PNG or GIF image." }

            val reader = readers.next()

            try {
                reader.input = input
                val format = reader.formatName.lowercase()

                require(format == "png" || format == "gif") { "Only PNG and GIF images are supported." }

                val stream = reader.streamMetadata?.let {
                    it.getAsTree(it.nativeMetadataFormatName)
                }

                val screen = stream?.child(name = "LogicalScreenDescriptor")
                val width = screen?.int(name = "logicalScreenWidth") ?: reader.getWidth(0)
                val height = screen?.int(name = "logicalScreenHeight") ?: reader.getHeight(0)

                require(width > 0 && height > 0 && width.toLong() * height <= 16_777_216L) {
                    "The image is too large. Use an image with at most 16 megapixels."
                }

                val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val graphics = canvas.createGraphics()

                try {
                    val count = reader.getNumImages(true)
                    val padCount = targets.sumOf { it.size.width.toLong() * it.size.height }

                    require(count in 1..10_000 && padCount * count <= 2_000_000L) {
                        "This animation is too large for the workspace. Use fewer frames or a smaller workspace."
                    }

                    val palette = stream?.child(name = "GlobalColorTable")
                    val backgroundIndex = palette?.int(name = "backgroundColorIndex")
                    val background = palette?.children()?.firstOrNull {
                        it.int(name = "index") == backgroundIndex
                    }?.let {
                        (0xFF shl 24) or (it.int(name = "red") shl 16) or
                                (it.int(name = "green") shl 8) or it.int(name = "blue")
                    } ?: 0

                    return List(size = count) { index ->
                        val metadata = reader.getImageMetadata(index).let {
                            it.getAsTree(it.nativeMetadataFormatName)
                        }

                        val descriptor = metadata.child(name = "ImageDescriptor")
                        val control = metadata.child(name = "GraphicControlExtension")
                        val left = descriptor?.int(name = "imageLeftPosition") ?: 0
                        val top = descriptor?.int(name = "imageTopPosition") ?: 0
                        val frameWidth = reader.getWidth(index)
                        val frameHeight = reader.getHeight(index)

                        require(left >= 0 && top >= 0 && left.toLong() + frameWidth <= width && top.toLong() + frameHeight <= height) {
                            "The GIF contains a frame outside its image bounds."
                        }

                        val disposal = control?.attribute(name = "disposalMethod")
                        val transparent = control?.attribute(name = "transparentColorFlag") == "TRUE"
                        val backgroundColor = if (transparent) {
                            0
                        } else {
                            background
                        }

                        if (index == 0 && format == "gif") {
                            graphics.composite = AlphaComposite.Src
                            graphics.color = java.awt.Color(backgroundColor, true)
                            graphics.fillRect(0, 0, width, height)
                        }

                        val previous = if (disposal == "restoreToPrevious") {
                            canvas.getRGB(0, 0, width, height, null, 0, width)
                        } else {
                            null
                        }

                        graphics.composite = AlphaComposite.SrcOver
                        graphics.drawImage(reader.read(index), left, top, null)

                        val frame = Frame(
                            timing = if (format == "png") {
                                Timing.Rythm(timing = Timing.Rythm.RythmTiming._1_4)
                            } else {
                                val delay = (control?.int(name = "delayTime") ?: 0) * 10
                                Timing.Duration(duration = (delay.takeIf { it > 0 } ?: 100).milliseconds)
                            },
                            entries = entries(image = canvas, bounds = bounds, targets = targets),
                        )

                        if (disposal == "restoreToBackgroundColor") {
                            graphics.composite = AlphaComposite.Src
                            graphics.color = java.awt.Color(backgroundColor, true)
                            graphics.fillRect(left, top, frameWidth, frameHeight)
                        } else if (previous != null) {
                            canvas.setRGB(0, 0, width, height, previous, 0, width)
                        }
                        frame
                    }
                } finally {
                    graphics.dispose()
                }
            } finally {
                reader.dispose()
            }
        }
    }

    private fun entries(
        image: BufferedImage,
        bounds: Pair<IntOffset, IntSize>,
        targets: List<Target>,
    ): List<KeyframesEntry> {
        val scale = min(bounds.second.width.toDouble() / image.width, bounds.second.height.toDouble() / image.height)
        val left = bounds.first.x + (bounds.second.width - image.width * scale) / 2.0
        val top = bounds.first.y + (bounds.second.height - image.height * scale) / 2.0

        return buildList {
            targets.forEach { target ->
                for (y in 0 until target.size.height) {
                    for (x in 0 until target.size.width) {
                        val globalX = target.position.x + x
                        val globalY = target.position.y + y

                        val sourceX = floor((globalX + 0.5 - left) / scale).toInt()
                        val sourceY = floor((globalY + 0.5 - top) / scale).toInt()

                        if (sourceX !in 0 until image.width || sourceY !in 0 until image.height) {
                            continue
                        }

                        val pixel = image.getRGB(sourceX, sourceY)
                        val alpha = (pixel ushr 24) / 255f

                        val r = ((pixel ushr 16) and 0xFF) / 255f * alpha
                        val g = ((pixel ushr 8) and 0xFF) / 255f * alpha
                        val b = (pixel and 0xFF) / 255f * alpha

                        if (r == 0f && g == 0f && b == 0f) {
                            continue
                        }

                        add(
                            element = KeyframesEntry(
                                x = globalX,
                                y = globalY,
                                r = r,
                                g = g,
                                b = b,
                                launchpadId = target.launchpadId,
                                localX = x,
                                localY = y,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun Node.children(): List<Node> = (0 until childNodes.length).map { childNodes.item(it) }

    private fun Node.child(name: String): Node? = children().firstOrNull { it.nodeName == name }

    private fun Node.attribute(name: String): String? = attributes?.getNamedItem(name)?.nodeValue

    private fun Node.int(name: String): Int = attribute(name = name)?.toIntOrNull() ?: 0
}
