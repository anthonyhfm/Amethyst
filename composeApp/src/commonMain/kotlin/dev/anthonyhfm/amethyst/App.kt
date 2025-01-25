package dev.anthonyhfm.amethyst

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import dev.anthonyhfm.amethyst.core.koin.amethystKoinModule
import dev.anthonyhfm.amethyst.editor.Editor
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

@Composable
@Preview
fun App() {
    KoinApplication(
        application = {
            modules(amethystKoinModule)
        }
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            Editor()
        }
    }
}