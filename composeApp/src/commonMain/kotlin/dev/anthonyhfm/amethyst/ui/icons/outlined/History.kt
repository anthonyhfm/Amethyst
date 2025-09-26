package dev.anthonyhfm.amethyst.ui.icons.outlined

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons

val AmethystIcons.Outlined.History: ImageVector
    get() {
        if (_IconName != null) {
            return _IconName!!
        }
        _IconName = ImageVector.Builder(
            name = "IconName",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color(0xFF1F1F1F))) {
                moveToRelative(472f, 648f)
                lineToRelative(56f, -56f)
                lineToRelative(-128f, -128f)
                verticalLineToRelative(-184f)
                horizontalLineToRelative(-80f)
                verticalLineToRelative(216f)
                lineToRelative(152f, 152f)
                close()
                moveTo(720f, 820f)
                verticalLineToRelative(-88f)
                quadToRelative(74f, -35f, 117f, -103f)
                reflectiveQuadToRelative(43f, -149f)
                quadToRelative(0f, -81f, -43f, -149f)
                reflectiveQuadTo(720f, 228f)
                verticalLineToRelative(-88f)
                quadToRelative(109f, 38f, 174.5f, 131.5f)
                reflectiveQuadTo(960f, 480f)
                quadToRelative(0f, 115f, -65.5f, 208.5f)
                reflectiveQuadTo(720f, 820f)
                close()
                moveTo(360f, 840f)
                quadToRelative(-75f, 0f, -140.5f, -28.5f)
                reflectiveQuadToRelative(-114f, -77f)
                quadToRelative(-48.5f, -48.5f, -77f, -114f)
                reflectiveQuadTo(0f, 480f)
                quadToRelative(0f, -75f, 28.5f, -140.5f)
                reflectiveQuadToRelative(77f, -114f)
                quadToRelative(48.5f, -48.5f, 114f, -77f)
                reflectiveQuadTo(360f, 120f)
                quadToRelative(75f, 0f, 140.5f, 28.5f)
                reflectiveQuadToRelative(114f, 77f)
                quadToRelative(48.5f, 48.5f, 77f, 114f)
                reflectiveQuadTo(720f, 480f)
                quadToRelative(0f, 75f, -28.5f, 140.5f)
                reflectiveQuadToRelative(-77f, 114f)
                quadToRelative(-48.5f, 48.5f, -114f, 77f)
                reflectiveQuadTo(360f, 840f)
                close()
                moveTo(360f, 760f)
                quadToRelative(117f, 0f, 198.5f, -81.5f)
                reflectiveQuadTo(640f, 480f)
                quadToRelative(0f, -117f, -81.5f, -198.5f)
                reflectiveQuadTo(360f, 200f)
                quadToRelative(-117f, 0f, -198.5f, 81.5f)
                reflectiveQuadTo(80f, 480f)
                quadToRelative(0f, 117f, 81.5f, 198.5f)
                reflectiveQuadTo(360f, 760f)
                close()
                moveTo(360f, 480f)
                close()
            }
        }.build()

        return _IconName!!
    }

@Suppress("ObjectPropertyName")
private var _IconName: ImageVector? = null
