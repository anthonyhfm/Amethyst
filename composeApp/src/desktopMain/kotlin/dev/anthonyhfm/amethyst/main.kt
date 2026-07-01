package dev.anthonyhfm.amethyst

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.application
import dev.anthonyhfm.amethyst.core.engine.echo.AudioOutput
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.DiscordRPCManager
import dev.anthonyhfm.amethyst.desktop.about.setupAboutHandler
import dev.anthonyhfm.amethyst.settings.setupPreferencesHandler
import dev.anthonyhfm.amethyst.start.EarlyAccessWindow
import dev.anthonyhfm.amethyst.settings.data.SettingsRepository
import dev.anthonyhfm.amethyst.start.StartWindow
import dev.anthonyhfm.amethyst.workspace.WorkspaceWindow
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenHelper
import dev.anthonyhfm.amethyst.workspace.utils.WorkspaceProjectOpenResult
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import java.awt.Desktop
import java.io.File
import javax.swing.SwingUtilities
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    initializeSentry()

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
        var macQuitRequest by remember { mutableIntStateOf(0) }
        var pendingMacQuitResponse by remember { mutableStateOf<java.awt.desktop.QuitResponse?>(null) }
        var hasAcceptedEarlyAccess by remember {
            mutableStateOf(
                SettingsRepository.platformSettings.getBoolean("early_access_accepted", false)
            )
        }

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

        LaunchedEffect(hasAcceptedEarlyAccess) {
            if (hasAcceptedEarlyAccess && args.isNotEmpty()) {
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

        if (!hasAcceptedEarlyAccess) {
            EarlyAccessWindow(
                onAccept = {
                    SettingsRepository.platformSettings.putBoolean("early_access_accepted", true)
                    hasAcceptedEarlyAccess = true
                },
                onCancel = {
                    exitProcess(0)
                }
            )
        } else if (!showEditor) {
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
