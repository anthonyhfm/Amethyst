package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken
import com.composeunstyled.theme.buildTheme

val colors = ThemeProperty<Color>("colors")
val background = ThemeToken<Color>("background")
val foreground = ThemeToken<Color>("foreground")
val card = ThemeToken<Color>("card")
val cardForeground = ThemeToken<Color>("card_foreground")
val popover = ThemeToken<Color>("popover")
val popoverForeground = ThemeToken<Color>("popover_foreground")
val primary = ThemeToken<Color>("primary")
val primaryForeground = ThemeToken<Color>("primary_foreground")
val secondary = ThemeToken<Color>("secondary")
val secondaryForeground = ThemeToken<Color>("secondary_foreground")
val muted = ThemeToken<Color>("muted")
val mutedForeground = ThemeToken<Color>("muted_foreground")
val accent = ThemeToken<Color>("accent")
val accentForeground = ThemeToken<Color>("accent_foreground")
val destructive = ThemeToken<Color>("destructive")
val destructiveForeground = ThemeToken<Color>("destructive_foreground")
val selectionSurface = ThemeToken<Color>("selection_surface")
val selectionForeground = ThemeToken<Color>("selection_foreground")
val selectionBorder = ThemeToken<Color>("selection_border")
val border = ThemeToken<Color>("border")
val input = ThemeToken<Color>("input")
val ring = ThemeToken<Color>("ring")
val chart1 = ThemeToken<Color>("chart_1")
val chart2 = ThemeToken<Color>("chart_2")
val chart3 = ThemeToken<Color>("chart_3")
val chart4 = ThemeToken<Color>("chart_4")
val chart5 = ThemeToken<Color>("chart_5")

val typography = ThemeProperty<TextStyle>("typography")
val h1 = ThemeToken<TextStyle>("h1")
val h2 = ThemeToken<TextStyle>("h2")
val h3 = ThemeToken<TextStyle>("h3")
val h4 = ThemeToken<TextStyle>("h4")
val p = ThemeToken<TextStyle>("p")
val blockquote = ThemeToken<TextStyle>("blockquote")
val lead = ThemeToken<TextStyle>("lead")
val large = ThemeToken<TextStyle>("large")
val small = ThemeToken<TextStyle>("small")
val mutedText = ThemeToken<TextStyle>("muted")
val inlineCode = ThemeToken<TextStyle>("inlineCode")

private fun AmethystColorPalette.toTokenMap(): Map<ThemeToken<Color>, Color> = mapOf(
    dev.anthonyhfm.amethyst.ui.theme.background to this.background,
    dev.anthonyhfm.amethyst.ui.theme.foreground to this.foreground,
    dev.anthonyhfm.amethyst.ui.theme.card to this.card,
    dev.anthonyhfm.amethyst.ui.theme.cardForeground to this.cardForeground,
    dev.anthonyhfm.amethyst.ui.theme.popover to this.popover,
    dev.anthonyhfm.amethyst.ui.theme.popoverForeground to this.popoverForeground,
    dev.anthonyhfm.amethyst.ui.theme.primary to this.primary,
    dev.anthonyhfm.amethyst.ui.theme.primaryForeground to this.primaryForeground,
    dev.anthonyhfm.amethyst.ui.theme.secondary to this.secondary,
    dev.anthonyhfm.amethyst.ui.theme.secondaryForeground to this.secondaryForeground,
    dev.anthonyhfm.amethyst.ui.theme.muted to this.muted,
    dev.anthonyhfm.amethyst.ui.theme.mutedForeground to this.mutedForeground,
    dev.anthonyhfm.amethyst.ui.theme.accent to this.accent,
    dev.anthonyhfm.amethyst.ui.theme.accentForeground to this.accentForeground,
    dev.anthonyhfm.amethyst.ui.theme.destructive to this.destructive,
    dev.anthonyhfm.amethyst.ui.theme.destructiveForeground to this.destructiveForeground,
    dev.anthonyhfm.amethyst.ui.theme.selectionSurface to this.selectionSurface,
    dev.anthonyhfm.amethyst.ui.theme.selectionForeground to this.selectionForeground,
    dev.anthonyhfm.amethyst.ui.theme.selectionBorder to this.selectionBorder,
    dev.anthonyhfm.amethyst.ui.theme.border to this.border,
    dev.anthonyhfm.amethyst.ui.theme.input to this.input,
    dev.anthonyhfm.amethyst.ui.theme.ring to this.ring,
    dev.anthonyhfm.amethyst.ui.theme.chart1 to this.chart1,
    dev.anthonyhfm.amethyst.ui.theme.chart2 to this.chart2,
    dev.anthonyhfm.amethyst.ui.theme.chart3 to this.chart3,
    dev.anthonyhfm.amethyst.ui.theme.chart4 to this.chart4,
    dev.anthonyhfm.amethyst.ui.theme.chart5 to this.chart5,
)

val AmethystLightTheme = buildTheme {
    name = "AmethystLight"
    properties[colors] = AmethystLightPalette.toTokenMap()
    properties[chainColorTokens] = lightChainColorMap
    properties[timelineColorTokens] = lightTimelineColorMap
    properties[timelineDimensionTokens] = timelineDimensionMap
}

val AmethystDarkTheme = buildTheme {
    name = "AmethystDark"
    properties[colors] = AmethystDarkPalette.toTokenMap()
    properties[chainColorTokens] = darkChainColorMap
    properties[timelineColorTokens] = darkTimelineColorMap
    properties[timelineDimensionTokens] = timelineDimensionMap
}

@Composable
fun AmethystTheme(darkMode: Boolean = true, content: @Composable () -> Unit) {
    val palette = amethystColorPalette(darkMode)
    val typ = rememberAmethystTypography()

    val theme = remember(darkMode, palette, typ) {
        buildTheme {
            name = if (darkMode) "AmethystDark" else "AmethystLight"
            properties[colors] = palette.toTokenMap()
            properties[chainColorTokens] = if (darkMode) darkChainColorMap else lightChainColorMap
            properties[timelineColorTokens] = if (darkMode) darkTimelineColorMap else lightTimelineColorMap
            properties[timelineDimensionTokens] = timelineDimensionMap
            properties[typography] = mapOf(
                h1 to typ.h1,
                h2 to typ.h2,
                h3 to typ.h3,
                h4 to typ.h4,
                p to typ.p,
                blockquote to typ.blockquote,
                lead to typ.lead,
                large to typ.large,
                small to typ.small,
                mutedText to typ.muted,
                inlineCode to typ.inlineCode,
            )
        }
    }

    theme { content() }
}
