package dev.anthonyhfm.amethyst.ui.icons.filled

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons

val AmethystIcons.Filled.Saddam: ImageVector
    get() {
        if (_IconName != null) {
            return _IconName!!
        }
        _IconName = ImageVector.Builder(
            name = "Saddam Houssein Hiding Spot",
            defaultWidth = 118.dp,
            defaultHeight = 24.dp,
            viewportWidth = 118f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFFD9D9D9))) {
                moveTo(0f, 10.5f)
                lineTo(4f, 5.5f)
                lineTo(14f, 4.5f)
                lineTo(18.5f, 8f)
                lineTo(21.5f, 11.5f)
                lineTo(26f, 9.5f)
                lineTo(31f, 8f)
                lineTo(42f, 6.5f)
                lineTo(53.5f, 8f)
                lineTo(70f, 9.5f)
                lineTo(82.5f, 11.5f)
                lineTo(102f, 12.5f)
                lineTo(107.5f, 11.5f)
                lineTo(114f, 0f)
                horizontalLineTo(118f)
                verticalLineTo(20f)
                lineTo(105f, 21f)
                lineTo(76.5f, 21.97f)
                lineTo(72.5f, 24f)
                horizontalLineTo(34.5f)
                lineTo(20.5f, 21.97f)
                lineTo(14f, 20f)
                horizontalLineTo(2.5f)
                lineTo(1f, 16.97f)
                lineTo(0f, 10.5f)
                close()
            }
        }.build()

        return _IconName!!
    }

@Suppress("ObjectPropertyName")
private var _IconName: ImageVector? = null
