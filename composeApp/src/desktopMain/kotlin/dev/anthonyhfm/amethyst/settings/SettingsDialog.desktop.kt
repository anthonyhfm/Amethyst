package dev.anthonyhfm.amethyst.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.anthonyhfm.amethyst.desktop.utility.DesktopUtilityWindowScaffold
import androidx.compose.ui.Modifier
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.nucleusframework.application.DecoratedDialog
import dev.nucleusframework.window.DialogTitleBar

@Composable
actual fun SettingsDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return

    DecoratedDialog(
        onCloseRequest = {
            onDismiss()
        },
        title = "Settings",
        state = rememberDialogState(
            width = 550.dp,
            height = 600.dp,
            position = WindowPosition.Aligned(Alignment.Center)
        ),
        resizable = false
    ) {
        DialogTitleBar {
            Text(
                text = "Settings",
                color = Theme[colors][foreground].copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        AppLocaleProvider {
            DesktopUtilityWindowScaffold {
                AppLocaleRefreshBoundary {
                    Settings()
                }
            }
        }
    }
}
