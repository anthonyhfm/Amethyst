package dev.anthonyhfm.amethyst.ui.icons.filled

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons

val AmethystIcons.Filled.Logo: ImageVector
    get() {
        if (_Frame != null) {
            return _Frame!!
        }
        _Frame = ImageVector.Builder(
            name = "Amethyst Logo",
            defaultWidth = 623.dp,
            defaultHeight = 482.dp,
            viewportWidth = 623f,
            viewportHeight = 482f
        ).apply {
            group(
                clipPathData = PathData {
                    moveTo(0f, 0f)
                    horizontalLineToRelative(623f)
                    verticalLineToRelative(482f)
                    horizontalLineToRelative(-623f)
                    close()
                }
            ) {
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 12f
                ) {
                    moveTo(80.47f, 326.83f)
                    curveTo(121.6f, 326.83f, 154.94f, 360.18f, 154.94f, 401.3f)
                    curveTo(154.94f, 442.43f, 121.6f, 475.77f, 80.47f, 475.77f)
                    curveTo(39.34f, 475.77f, 6f, 442.43f, 6f, 401.3f)
                    curveTo(6f, 360.18f, 39.34f, 326.83f, 80.47f, 326.83f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 12f
                ) {
                    moveTo(144.41f, 183.04f)
                    lineTo(246.14f, 37.76f)
                    curveTo(269.73f, 4.07f, 316.16f, -4.12f, 349.85f, 19.47f)
                    curveTo(383.54f, 43.06f, 391.73f, 89.5f, 368.14f, 123.19f)
                    lineTo(266.42f, 268.47f)
                    curveTo(242.83f, 302.16f, 196.39f, 310.35f, 162.7f, 286.76f)
                    curveTo(129.01f, 263.17f, 120.82f, 216.73f, 144.41f, 183.04f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Black),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 12f
                ) {
                    moveTo(501.39f, 213.16f)
                    lineTo(603.11f, 358.44f)
                    curveTo(626.7f, 392.13f, 618.51f, 438.56f, 584.82f, 462.15f)
                    curveTo(551.13f, 485.74f, 504.7f, 477.56f, 481.11f, 443.87f)
                    lineTo(379.38f, 298.59f)
                    curveTo(355.79f, 264.9f, 363.98f, 218.46f, 397.67f, 194.87f)
                    curveTo(431.36f, 171.28f, 477.8f, 179.47f, 501.39f, 213.16f)
                    close()
                }
            }
        }.build()

        return _Frame!!
    }

@Suppress("ObjectPropertyName")
private var _Frame: ImageVector? = null
