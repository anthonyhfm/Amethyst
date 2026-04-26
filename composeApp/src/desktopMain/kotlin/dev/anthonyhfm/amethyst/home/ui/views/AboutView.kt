package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.displayString
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyH2
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import org.jetbrains.compose.resources.painterResource

@Composable
fun AboutView() {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Image(
                painter = painterResource(Res.drawable.amethyst_studio_logo),
                contentDescription = "Amethyst Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(160.dp),
            )

            TypographyH2(
                text = "Amethyst Studio",
                modifier = Modifier.padding(top = 8.dp),
            )

            TypographyMuted("Version ${amethystVersion.displayString}")
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            TypographyMuted("Made with love by anthonyhfm")
        }
    }
}
