package dev.anthonyhfm.amethyst.ui.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons

val AmethystIcons.Outlined.Metronome: ImageVector
    get() {
        if (_Metronome != null) {
            return _Metronome!!
        }
        _Metronome = ImageVector.Builder(
            name = "Metronome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 1.75f)
                lineTo(8.57f, 2.67f)
                lineTo(4.06f, 19.53f)
                curveTo(4.03f, 19.68f, 4f, 19.84f, 4f, 20f)
                curveTo(4f, 21.11f, 4.89f, 22f, 6f, 22f)
                horizontalLineTo(18f)
                curveTo(19.11f, 22f, 20f, 21.11f, 20f, 20f)
                curveTo(20f, 19.84f, 19.97f, 19.68f, 19.94f, 19.53f)
                lineTo(18.58f, 14.42f)
                lineTo(17f, 16f)
                lineTo(17.2f, 17f)
                horizontalLineTo(13.41f)
                lineTo(16.25f, 14.16f)
                lineTo(14.84f, 12.75f)
                lineTo(10.59f, 17f)
                horizontalLineTo(6.8f)
                lineTo(10.29f, 4f)
                horizontalLineTo(13.71f)
                lineTo(15.17f, 9.43f)
                lineTo(16.8f, 7.79f)
                lineTo(15.43f, 2.67f)
                lineTo(12f, 1.75f)
                moveTo(11.25f, 5f)
                verticalLineTo(14.75f)
                lineTo(12.75f, 13.25f)
                verticalLineTo(5f)
                horizontalLineTo(11.25f)
                moveTo(19.79f, 7.8f)
                lineTo(16.96f, 10.63f)
                lineTo(16.25f, 9.92f)
                lineTo(14.84f, 11.34f)
                lineTo(17.66f, 14.16f)
                lineTo(19.08f, 12.75f)
                lineTo(18.37f, 12.04f)
                lineTo(21.2f, 9.21f)
                lineTo(19.79f, 7.8f)
                close()
            }
        }.build()

        return _Metronome!!
    }

@Suppress("ObjectPropertyName")
private var _Metronome: ImageVector? = null
