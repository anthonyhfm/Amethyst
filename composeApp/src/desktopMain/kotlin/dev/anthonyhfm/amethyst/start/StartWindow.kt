package dev.anthonyhfm.amethyst.start

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_linux
import amethyst.composeapp.generated.resources.amethyst_windows
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.FlatUtilityLaf
import dev.anthonyhfm.amethyst.start.ui.AmethystWelcome
import dev.anthonyhfm.amethyst.start.ui.LoadingScreen
import dev.anthonyhfm.amethyst.start.ui.ProjectsView
import dev.anthonyhfm.amethyst.ui.modifier.platformPaddingTop
import org.jetbrains.compose.resources.painterResource
import javax.swing.UIManager
import kotlin.system.exitProcess

@Composable
fun StartWindow(
    onOpenEditor: () -> Unit,
) {
    if (DesktopPlatform.get() == DesktopPlatform.Windows) {
        UIManager.setLookAndFeel(FlatUtilityLaf())
    }

    Window(
        onCloseRequest = {
            exitProcess(0)
        },
        title = "Amethyst",
        state = rememberWindowState(
            width = 700.dp,
            height = 450.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        resizable = false,
        icon = when (DesktopPlatform.get()) {
            DesktopPlatform.Windows -> painterResource(Res.drawable.amethyst_windows)
            DesktopPlatform.Linux -> painterResource(Res.drawable.amethyst_linux)

            else -> null
        }
    ) {
        val viewModel = viewModel { StartWindowViewModel() }
        val loadingMessage: String? by viewModel.loadingState.collectAsState()
        val showAbletonImportWizard: Boolean by viewModel.showAbletonImportWizard.collectAsState()

        viewModel.onOpenEditor = {
            onOpenEditor()
        }

        if (DesktopPlatform.get() == DesktopPlatform.MacOS) {
            window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        }

        if (showAbletonImportWizard) {
            AbletonImportWizard(
                viewModel.abletonSetPath.value, // pass full path so the wizard can read the file
                onStartConversion = { customPalettePath ->
                    viewModel.startAbletonConversion(customPalettePath)
                },
                onCancel = {
                    viewModel.showAbletonImportWizard.value = false
                }
            )
        }

        if (loadingMessage != null) {
            LoadingScreen(loadingMessage!!)
        }

        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .platformPaddingTop()
                ) {
                    AmethystWelcome(
                        onClickGitHub = {
                            viewModel.openGitHubWebsite()
                        }
                    )

                    ProjectsView(
                        onClickCreateProject = {
                            viewModel.onClickCreateProject()
                        },
                        onClickOpenProject = {
                            viewModel.onClickOpenProject()
                        },
                        onOpenRecentWorkspace = {
                            viewModel.openProjectFile(it.path)
                        },
                        onRemoveProjectFromRecents = {
                            viewModel.onRemoveProjectFromRecents(it)
                        }
                    )
                }
            }
        }
    }
}