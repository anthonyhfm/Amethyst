package dev.anthonyhfm.amethyst

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.core.koin.amethystKoinModule
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.OSXTitleBar
import dev.anthonyhfm.amethyst.editor.Editor
import dev.anthonyhfm.amethyst.editor.EditorWindow
import dev.anthonyhfm.amethyst.start.StartWindow
import org.koin.compose.KoinApplication

fun main() {
    val platform = DesktopPlatform.get()

    if (platform == DesktopPlatform.MacOS) {
        System.setProperty("apple.awt.application.name", "Amethyst")
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")
    }

    application {
        KoinApplication(
            application = {
                modules(amethystKoinModule)
            }
        ) {
            var showEditor: Boolean by remember { mutableStateOf(false) }

            if (!showEditor) {
                StartWindow(
                    onOpenEditor = {
                        showEditor = true
                    }
                )
            } else {
                EditorWindow()
            }
        }
    }
}