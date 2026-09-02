package dev.anthonyhfm.amethyst.desktop.utility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.nucleusframework.window.styling.TitleBarColors
import dev.nucleusframework.window.styling.TitleBarMetrics
import dev.nucleusframework.window.styling.TitleBarStyle

@Composable
fun rememberTitleBarStyle(): TitleBarStyle {
    val background = Theme[colors][background]
    val foreground = Theme[colors][foreground]

    val style = remember(background, foreground) {
        TitleBarStyle(
            colors = TitleBarColors(
                background = background,
                inactiveBackground = background,
                content = foreground,
                border = Color.Transparent,
            ),
            metrics = TitleBarMetrics(),
        )
    }

    return style
}
