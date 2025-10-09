package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.theme.AMETHYST_THEME
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoadingScreenView(message: String) {
    MaterialTheme(
        colorScheme = AMETHYST_THEME
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
            ) {
                Image(
                    painter = painterResource(Res.drawable.amethyst_studio_logo),
                    contentDescription = "Loading",
                    modifier = Modifier.size(100.dp)
                )

                Text(message)

                LinearProgressIndicator(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(264.dp)
                )
            }
        }
    }
}