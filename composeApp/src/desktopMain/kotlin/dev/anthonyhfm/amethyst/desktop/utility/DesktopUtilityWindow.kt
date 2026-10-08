package dev.anthonyhfm.amethyst.desktop.utility

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors

@Composable
fun DesktopUtilityWindowScaffold(
    titleBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AmethystTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme[colors][background])
        ) {
            titleBar?.invoke()

            Box(
                modifier = Modifier
                    .weight(weight = 1f)
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
