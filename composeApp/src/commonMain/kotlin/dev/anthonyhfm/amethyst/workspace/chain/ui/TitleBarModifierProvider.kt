package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * CompositionLocal für den Modifier der Titelleiste der Geräte im Chain Editor
 */
val LocalTitleBarModifier = compositionLocalOf<Modifier> { Modifier }

/**
 * Provider für den Modifier der Titelleiste
 * Ermöglicht es, einen Modifier für die Titelleiste von außen bereitzustellen
 */
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
