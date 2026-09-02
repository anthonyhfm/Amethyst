package dev.anthonyhfm.amethyst

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.DiscordRPCManager
import dev.anthonyhfm.amethyst.desktop.utility.rememberTitleBarStyle
import dev.anthonyhfm.amethyst.settings.data.AudioSettings
import dev.anthonyhfm.amethyst.start.StartWindow
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceWindow
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenHelper
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenResult
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import java.awt.Desktop
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    initializeSentry()

    val platform = DesktopPlatform.get()

    Echo.setPreferredBufferFrames(AudioSettings.renderBufferFrames.value)
    Echo.setPreferredOutputDevice(
        AudioSettings.outputDevice.value.takeUnless { it == AudioSettings.SystemDefaultOutputDevice }
    )
    Echo.setExclusiveMode(AudioSettings.exclusiveMode?.value == true)
    Echo.initialize()
    
    DiscordRPCManager.initialize()

    nucleusApplication(backend = NucleusBackend.Tao) {
        FileKit.init(appId = "Amethyst")

        var showEditor: Boolean by remember { mutableStateOf(false) }
        var macQuitRequest by remember { mutableIntStateOf(0) }
        var pendingMacQuitResponse by remember { mutableStateOf<java.awt.desktop.QuitResponse?>(null) }

        LaunchedEffect(Unit) {
            if (
                platform == DesktopPlatform.MacOS &&
                Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)
            ) {
                Desktop.getDesktop().setQuitHandler { _, response ->
                    SwingUtilities.invokeLater {
                        if (showEditor) {
                            pendingMacQuitResponse = response
                            macQuitRequest += 1
                        } else {
                            response.performQuit()
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            if (args.isNotEmpty()) {
                val file = File(args[0])

                if (file.exists() && file.isFile) {
                    val result = WorkspaceProjectOpenHelper.openProject(
                        PlatformFile(file)
                    )

                    if (result is WorkspaceProjectOpenResult.Success) {
                        showEditor = true
                    }
                }
            }
        }

        AmethystTheme {
            NucleusDecoratedWindowTheme(
                isDark = true,
                titleBarStyle = rememberTitleBarStyle()
            ) {
                if (!showEditor) {
                    StartWindow(
                        onOpenEditor = {
                            showEditor = true
                        }
                    )
                } else {
                    WorkspaceWindow(
                        externalCloseRequest = macQuitRequest,
                        onExternalCloseConfirmed = {
                            showEditor = false
                            pendingMacQuitResponse?.performQuit()
                            pendingMacQuitResponse = null
                        },
                        onExternalCloseCancelled = {
                            pendingMacQuitResponse?.cancelQuit()
                            pendingMacQuitResponse = null
                        },
                        onClose = {
                            showEditor = false
                        }
                    )
                }
            }
        }
    }
}
