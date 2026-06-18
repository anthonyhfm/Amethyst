package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.ui.theme.small

@Composable
actual fun OriginIndicator(
    modifier: Modifier,
) {
    val originBackground = Color(0xFF282C34)
    val originForeground = Color(0xFFABB2BF).copy(alpha = 0.82f)
    val viewportBorder = Color.Transparent

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(originBackground)
            .border(1.dp, viewportBorder, CircleShape)
    ) {
        Text(
            text = "0,0",
            modifier = Modifier.align(Alignment.Center),
            style = Theme[typography][small],
            color = originForeground,
        )
    }
}
