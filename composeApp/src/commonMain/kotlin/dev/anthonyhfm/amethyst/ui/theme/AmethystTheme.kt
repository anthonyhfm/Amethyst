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

private val lightColorMap = mapOf(
    background to Color(0xFFFFFFFF),
    foreground to Color(0xFF030712),
    card to Color(0xFFFFFFFF),
    cardForeground to Color(0xFF030712),
    popover to Color(0xFFFFFFFF),
    popoverForeground to Color(0xFF030712),
    primary to Color(0xFF7C3AED),
    primaryForeground to Color(0xFFF9FAFB),
    secondary to Color(0xFFF3F4F6),
    secondaryForeground to Color(0xFF111827),
    muted to Color(0xFFF3F4F6),
    mutedForeground to Color(0xFF6B7280),
    accent to Color(0xFFF3F4F6),
    accentForeground to Color(0xFF111827),
    destructive to Color(0xFFEF4444),
    destructiveForeground to Color(0xFFF9FAFB),
    selectionSurface to Color(0xFF2563EB),
    selectionForeground to Color(0xFFF8FAFC),
    selectionBorder to Color(0xFF1D4ED8),
    border to Color(0xFFE5E7EB),
    input to Color(0xFFE5E7EB),
    ring to Color(0xFF7C3AED),
    chart1 to Color(0xFFE76E50),
    chart2 to Color(0xFF2A9D90),
    chart3 to Color(0xFF274754),
    chart4 to Color(0xFFE8C468),
    chart5 to Color(0xFFF4A362),
)

private val darkColorMap = mapOf(
    background to Color(0xFF030712),
    foreground to Color(0xFFF9FAFB),
    card to Color(0xFF030712),
    cardForeground to Color(0xFFF9FAFB),
    popover to Color(0xFF030712),
    popoverForeground to Color(0xFFF9FAFB),
    primary to Color(0xFF6D28D9),
    primaryForeground to Color(0xFFF9FAFB),
    secondary to Color(0xFF1F2937),
    secondaryForeground to Color(0xFFF9FAFB),
    muted to Color(0xFF1F2937),
    mutedForeground to Color(0xFF9CA3AF),
    accent to Color(0xFF1F2937),
    accentForeground to Color(0xFFF9FAFB),
    destructive to Color(0xFF7F1D1D),
    destructiveForeground to Color(0xFFF9FAFB),
    selectionSurface to Color(0xFF60A5FA),
    selectionForeground to Color(0xFF0B1220),
    selectionBorder to Color(0xFF93C5FD),
    border to Color(0xFF1F2937),
    input to Color(0xFF1F2937),
    ring to Color(0xFF6D28D9),
    chart1 to Color(0xFF2662D9),
    chart2 to Color(0xFF2EB88A),
    chart3 to Color(0xFFE88C30),
    chart4 to Color(0xFFAF57DB),
    chart5 to Color(0xFFE23670),
)

val AmethystLightTheme = buildTheme {
    name = "AmethystLight"
    properties[colors] = lightColorMap
    properties[chainColorTokens] = lightChainColorMap
    properties[timelineColorTokens] = lightTimelineColorMap
    properties[timelineDimensionTokens] = timelineDimensionMap
}

val AmethystDarkTheme = buildTheme {
    name = "AmethystDark"
    properties[colors] = darkColorMap
    properties[chainColorTokens] = darkChainColorMap
    properties[timelineColorTokens] = darkTimelineColorMap
    properties[timelineDimensionTokens] = timelineDimensionMap
}

@Composable
fun AmethystTheme(darkMode: Boolean = true, content: @Composable () -> Unit) {
    val roboto = rememberRobotoFontFamily()
    val typ = remember(roboto) { buildAmethystTypography(roboto) }

    val theme = remember(darkMode, typ) {
        buildTheme {
            name = if (darkMode) "AmethystDark" else "AmethystLight"
            properties[colors] = if (darkMode) darkColorMap else lightColorMap
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
