package dev.anthonyhfm.amethyst

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import dev.anthonyhfm.amethyst.core.koin.amethystKoinModule
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.start.StartWindow
import dev.anthonyhfm.amethyst.workspace.WorkspaceWindow
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
            var showEditor: Boolean by remember { mutableStateOf(true) }

            if (!showEditor) {
                StartWindow(
                    onOpenEditor = {
                        showEditor = true
                    }
                )
            } else {
                WorkspaceWindow()
            }
        }
    }
}