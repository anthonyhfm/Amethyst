package dev.anthonyhfm.amethyst.start

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_linux
import amethyst.composeapp.generated.resources.amethyst_windows
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.anthonyhfm.amethyst.home.Home
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.anthonyhfm.amethyst.home.HomeCommandSurface
import dev.anthonyhfm.amethyst.settings.AppLocaleProvider
import org.jetbrains.compose.resources.painterResource
import kotlin.system.exitProcess
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode

@Composable
fun StartWindow(
    onOpenEditor: () -> Unit,
) {
    DecoratedWindow(
        onCloseRequest = {
            exitProcess(0)
        },
        title = "Amethyst",
        state = rememberWindowState(
            width = 750.dp,
            height = 550.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        icon = when (DesktopPlatform.get()) {
            DesktopPlatform.Windows -> painterResource(Res.drawable.amethyst_windows)
            DesktopPlatform.Linux -> painterResource(Res.drawable.amethyst_linux)

            else -> null
        },
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && (event.isCtrlPressed || event.isMetaPressed)) {
                when (event.key) {
                    Key.N -> { HomeCommandSurface.emit(HomeCommandSurface.HomeCommand.NewProject); true }
                    Key.O -> { HomeCommandSurface.emit(HomeCommandSurface.HomeCommand.OpenProject); true }
                    else -> false
                }
            } else false
        }
    ) {
        WindowAppearance(WindowAppearanceMode.Dark)

        TitleBar { state ->
            Text(
                text = "Amethyst - Home",
                color = Theme[colors][foreground].copy(0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        LaunchedEffect(Unit) {
            nucleusWindow.setMinimumSize(DpSize(750.dp, 550.dp))
        }

        AppLocaleProvider {
            Home(
                onOpenWorkspace = {
                    onOpenEditor()
                }
            )
        }
    }
}
