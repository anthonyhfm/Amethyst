package dev.anthonyhfm.amethyst.desktop.about

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_linux
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.displayString
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import com.composeunstyled.theme.Theme
import com.composeunstyled.Text
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import dev.nucleusframework.application.DecoratedDialog
import dev.nucleusframework.window.DialogTitleBar

@Composable
fun AboutDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    DecoratedDialog(
        onCloseRequest = onDismiss,
        title = "About Amethyst",
        state = rememberDialogState(
            width = 320.dp,
            height = 260.dp,
            position = WindowPosition.Aligned(Alignment.Center),
        ),
        resizable = false,
    ) {
        DialogTitleBar {
            Text(
                text = "About Amethyst",
                color = Theme[colors][foreground].copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        AboutDialogContent()
    }
}

@Composable
private fun AboutDialogContent() {
    AmethystTheme {
        val backgroundColor = Theme[colors][background]
        val titleColor = Theme[colors][foreground]
        val versionColor = Theme[colors][mutedForeground]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.amethyst_linux),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                BasicText(
                    text = "Amethyst",
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                BasicText(
                    text = "Version ${amethystVersion.displayString}",
                    style = TextStyle(
                        color = versionColor,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                BasicText(
                    text = "Made by Anthony Hofmeister",
                    style = TextStyle(
                        color = versionColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                )
            }
        }
    }
}
