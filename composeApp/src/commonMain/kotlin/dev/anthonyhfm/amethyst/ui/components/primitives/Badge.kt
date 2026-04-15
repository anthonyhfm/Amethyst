package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.destructiveForeground
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

enum class BadgeVariant {
    Default,
    Secondary,
    Destructive,
    Outline,
}

@Composable
fun Badge(
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
    content: @Composable () -> Unit,
) {
    val bg: Color
    val fg: Color
    val borderMod: Modifier

    when (variant) {
        BadgeVariant.Default -> {
            bg = Theme[colors][primary]
            fg = Theme[colors][primaryForeground]
            borderMod = Modifier
        }
        BadgeVariant.Secondary -> {
            bg = Theme[colors][secondary]
            fg = Theme[colors][secondaryForeground]
            borderMod = Modifier
        }
        BadgeVariant.Destructive -> {
            bg = Theme[colors][destructive]
            fg = Theme[colors][destructiveForeground]
            borderMod = Modifier
        }
        BadgeVariant.Outline -> {
            bg = Color.Transparent
            fg = Theme[colors][foreground]
            borderMod = Modifier.border(1.dp, Theme[colors][border], FullShape)
        }
    }

    Box(
        modifier = modifier
            .clip(FullShape)
            .then(borderMod)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fg)) {
            content()
        }
    }
}
