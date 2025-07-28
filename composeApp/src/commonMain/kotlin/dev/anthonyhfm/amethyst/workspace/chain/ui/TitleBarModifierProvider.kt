package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalTitleBarModifier = compositionLocalOf<Modifier> { Modifier }

@Composable
fun TitleBarModifierProvider(
    titleBarModifier: Modifier,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalTitleBarModifier provides titleBarModifier
    ) {
        content()
    }
}
