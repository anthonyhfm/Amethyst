package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.runtime.Composable

@Composable
fun ComposeAmethystTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    AmethystTheme(
        darkMode = darkMode,
        content = content,
    )
}

