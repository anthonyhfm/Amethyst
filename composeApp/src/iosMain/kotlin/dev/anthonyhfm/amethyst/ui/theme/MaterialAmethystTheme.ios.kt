package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformMaterialTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit,
) {
    content()
}
