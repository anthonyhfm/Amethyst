package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

enum class AlertVariant {
    Default,
    Destructive,
}

private val LocalAlertForeground = compositionLocalOf<Color?> { null }

@Composable
fun Alert(
    modifier: Modifier = Modifier,
    variant: AlertVariant = AlertVariant.Default,
    icon: ImageVector? = null,
    content: @Composable () -> Unit,
) {
    val borderColor = when (variant) {
        AlertVariant.Default -> Theme[colors][border]
        AlertVariant.Destructive -> Theme[colors][destructive].copy(alpha = 0.5f)
    }
    val fg = when (variant) {
        AlertVariant.Default -> Theme[colors][cardForeground]
        AlertVariant.Destructive -> Theme[colors][destructive]
    }

    CompositionLocalProvider(LocalAlertForeground provides fg) {
        Row(
            modifier = modifier
                .clip(DefaultShape)
                .border(1.dp, borderColor, DefaultShape)
                .background(Theme[colors][card])
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fg,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProvideTextStyle(Theme[typography][small].copy(color = fg)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun AlertTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val fg = LocalAlertForeground.current ?: Theme[colors][foreground]

    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][p].copy(
            fontWeight = FontWeight.Medium,
            color = fg,
        ),
    )
}

@Composable
fun AlertDescription(
    text: String,
    modifier: Modifier = Modifier,
) {
    val fg = (LocalAlertForeground.current ?: Theme[colors][foreground]).copy(alpha = 0.9f)

    Text(
        text = text,
        modifier = modifier,
        style = Theme[typography][small].copy(color = fg),
    )
}
