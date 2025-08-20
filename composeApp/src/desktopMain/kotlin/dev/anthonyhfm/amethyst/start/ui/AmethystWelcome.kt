package dev.anthonyhfm.amethyst.start.ui

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import amethyst.composeapp.generated.resources.github_mark
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ModalDrawer
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

@Composable
fun AmethystWelcome(onClickGitHub: () -> Unit) {
    val version = System.getProperty("app.version")

    Box(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
    ) {
        Text(
            text = if (version == null) {
                "Private Beta 2"
            } else {
                "Version: $version"
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Image(
            painter = painterResource(Res.drawable.amethyst_studio_logo),
            contentDescription = "Amethyst Studio Logo",
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
        )

        IconButton(
            onClick = {
                onClickGitHub()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.github_mark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}