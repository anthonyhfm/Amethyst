package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformMaterialTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit,
)

@Composable
fun ComposeAmethystTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    AmethystTheme(
        darkMode = darkMode,
    ) {
        PlatformMaterialTheme(
            darkMode = darkMode,
            content = content,
        )
    }
}

