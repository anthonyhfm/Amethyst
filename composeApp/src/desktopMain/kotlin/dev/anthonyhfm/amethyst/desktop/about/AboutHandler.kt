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
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.displayString
import dev.anthonyhfm.amethyst.desktop.DesktopPlatform
import dev.anthonyhfm.amethyst.desktop.utility.configureDesktopUtilityDialog
import dev.anthonyhfm.amethyst.ui.theme.AmethystTheme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import java.awt.Desktop
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import com.composeunstyled.theme.Theme
import org.jetbrains.compose.resources.painterResource

private var aboutDialog: ComposeDialog? = null

fun setupAboutHandler() {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()

        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler {
                showAboutDialog()
            }
        }
    }
}

fun showAboutDialog() {
    aboutDialog
        ?.takeIf { it.isDisplayable }
        ?.let { dialog ->
            dialog.toFront()
            dialog.requestFocus()
            return
        }

    val dialog = ComposeDialog()
    configureDesktopUtilityDialog(
        dialog = dialog,
        title = "About Amethyst",
        width = 320,
        height = 260,
        resizable = false
    )
    dialog.addWindowListener(object : WindowAdapter() {
        override fun windowClosed(event: WindowEvent?) {
            if (aboutDialog === dialog) {
                aboutDialog = null
            }
        }
    })
    dialog.setContent {
        AboutDialogContent()
    }

    aboutDialog = dialog
    dialog.show()
}

@Composable
private fun AboutDialogContent() {
    val topPadding = if (DesktopPlatform.get() == DesktopPlatform.MacOS) 28.dp else 20.dp

    AmethystTheme {
        val backgroundColor = Theme[colors][background]
        val titleColor = Theme[colors][foreground]
        val versionColor = Theme[colors][mutedForeground]
        val authorColor = Theme[colors][primary]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(start = 24.dp, top = topPadding, end = 24.dp, bottom = 24.dp),
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
