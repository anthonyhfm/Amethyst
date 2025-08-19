package dev.anthonyhfm.amethyst

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.formdev.flatlaf.FlatPropertiesLaf
import com.formdev.flatlaf.ui.FlatTitlePane
import com.formdev.flatlaf.util.SystemInfo
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.koin.amethystKoinModule
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.DiscordRPC
import dev.anthonyhfm.amethyst.desktop.FlatAmethystLaf
import dev.anthonyhfm.amethyst.desktop.about.setupAboutHandler
import dev.anthonyhfm.amethyst.start.StartWindow
import dev.anthonyhfm.amethyst.workspace.WorkspaceWindow
import io.github.vinceglb.filekit.FileKit
import org.koin.compose.KoinApplication
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager


fun main() {
    val platform = DesktopPlatform.get()
    val rpc = DiscordRPC()


    if (platform == DesktopPlatform.MacOS) {
        System.setProperty("apple.awt.application.name", "Amethyst")
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua")

        setupAboutHandler()
    }

    application {
        if (GlobalSettings.enableDiscordRPC) {
            rpc.start()
        }

        FileKit.init(appId = "Amethyst")

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
                WorkspaceWindow()
            }
        }
    }
}