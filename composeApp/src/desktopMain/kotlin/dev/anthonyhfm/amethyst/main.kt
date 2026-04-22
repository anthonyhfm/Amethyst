package dev.anthonyhfm.amethyst

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.DiscordRPCManager
import dev.anthonyhfm.amethyst.desktop.about.setupAboutHandler
import dev.anthonyhfm.amethyst.settings.setupPreferencesHandler
import dev.anthonyhfm.amethyst.start.StartWindow
import dev.anthonyhfm.amethyst.workspace.WorkspaceWindow
import io.github.vinceglb.filekit.FileKit

fun main() {
    val platform = DesktopPlatform.get()

    if (platform == DesktopPlatform.MacOS) {
        System.setProperty("apple.awt.application.name", "Amethyst")
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")

        setupAboutHandler()
        setupPreferencesHandler()
    }

    AudioOutput // <- Initialize AudioOutput, no constructor call, just reference
    
    // Initialize Discord RPC manager (will connect if enabled in settings)
    DiscordRPCManager.initialize()

    application {
        FileKit.init(appId = "Amethyst")

        var showEditor: Boolean by remember { mutableStateOf(false) }

        if (!showEditor) {
            StartWindow(
                onOpenEditor = {
                    showEditor = true
                }
            )
        } else {
            WorkspaceWindow(
                onClose = {
                    showEditor = false
                }
            )
        }
    }
}
