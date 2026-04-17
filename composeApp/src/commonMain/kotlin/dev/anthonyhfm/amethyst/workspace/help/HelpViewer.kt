package dev.anthonyhfm.amethyst.workspace.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import amethyst.composeapp.generated.resources.Res
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground

@Composable
internal fun HelpViewer(
    helpRef: String,
    paddingValues: PaddingValues,
    onClose: () -> Unit,
) {
    var markdownContent by remember(helpRef) { mutableStateOf<String?>(null) }
    var loadError by remember(helpRef) { mutableStateOf(false) }

    LaunchedEffect(helpRef) {
        try {
            val bytes = Res.readBytes("files/devices/$helpRef.md")
            markdownContent = bytes.decodeToString()
        } catch (e: Exception) {
            loadError = true
            println("HelpViewer: failed to load help for '$helpRef': ${e.message}")
        }
    }

    val bg = Theme[colors][background]
    val fg = Theme[colors][foreground]
    val mutedBg = Theme[colors][muted]
    val mutedFg = Theme[colors][mutedForeground]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Close button bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Button(
                    onClick = onClose,
                    variant = ButtonVariant.Ghost,
                    size = ButtonSize.Icon,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Lucide.X,
                        contentDescription = "Close help",
                        tint = fg,
                    )
                }
            }

            // Markdown content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                when {
                    loadError -> {
                        Text(
                            text = "Could not load help content.",
                            color = mutedFg,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                    markdownContent != null -> {
                        Markdown(
                            content = markdownContent!!,
                            imageTransformer = ResourceImageTransformer,
                            colors = markdownColor(
                                text = fg,
                                codeBackground = mutedBg,
                                inlineCodeBackground = mutedBg,
                                dividerColor = mutedBg,
                            ),
                            typography = markdownTypography(
                                h1 = MaterialTheme.typography.headlineLarge.copy(color = fg),
                                h2 = MaterialTheme.typography.headlineMedium.copy(color = fg),
                                h3 = MaterialTheme.typography.headlineSmall.copy(color = fg),
                                h4 = MaterialTheme.typography.titleLarge.copy(color = fg),
                                h5 = MaterialTheme.typography.titleMedium.copy(color = fg),
                                h6 = MaterialTheme.typography.titleSmall.copy(color = fg),
                                text = MaterialTheme.typography.bodyLarge.copy(color = fg),
                                code = MaterialTheme.typography.bodyMedium.copy(color = fg),
                                quote = MaterialTheme.typography.bodyLarge.copy(color = mutedFg),
                                paragraph = MaterialTheme.typography.bodyLarge.copy(color = fg),
                                ordered = MaterialTheme.typography.bodyLarge.copy(color = fg),
                                bullet = MaterialTheme.typography.bodyLarge.copy(color = fg),
                                list = MaterialTheme.typography.bodyLarge.copy(color = fg),
                                table = MaterialTheme.typography.bodyMedium.copy(color = fg),
                            ),
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
