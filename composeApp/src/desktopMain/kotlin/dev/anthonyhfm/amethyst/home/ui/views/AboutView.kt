package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.amethyst_studio_logo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.displayString
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyH2
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.ui.icons.AmethystIcons
import dev.anthonyhfm.amethyst.ui.icons.Signature
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primary
import org.jetbrains.compose.resources.painterResource
import java.awt.Desktop
import java.net.URI

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
            Box(
                modifier = Modifier
                    .clickable {
                        Desktop.getDesktop().browse(URI("https://github.com/anthonyhfm/amethyst"))
                    }
            ) {
                Icon(
                    imageVector = AmethystIcons.Filled.Signature,
                    contentDescription = null,
                    modifier = Modifier
                        .height(64.dp)
                        .blur(16.dp),
                    tint = Color(0xFF8B5CF6).copy(alpha = 0.6f)
                )

                Icon(
                    imageVector = AmethystIcons.Filled.Signature,
                    contentDescription = "Signature",
                    modifier = Modifier
                        .height(64.dp)
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithCache {
                            val brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8B5CF6), // Purple
                                    Color(0xFFD8B4FE)  // Light purple
                                ),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f)
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush, blendMode = BlendMode.SrcIn)
                            }
                        },
                    tint = Color.Black // Tint replaced by SrcIn
                )
            }
        }
    }
}
