package dev.anthonyhfm.amethyst.ui.theme

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.Roboto_Italic_VariableFont
import amethyst.composeapp.generated.resources.Roboto_VariableFont
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font

data class AmethystTypography(
    /** h1 — text-4xl font-extrabold tracking-tight */
    val h1: TextStyle,
    /** h2 — text-3xl font-semibold tracking-tight */
    val h2: TextStyle,
    /** h3 — text-2xl font-semibold tracking-tight */
    val h3: TextStyle,
    /** h4 — text-xl font-semibold tracking-tight */
    val h4: TextStyle,
    /** Body paragraph — leading-7 */
    val p: TextStyle,
    /** Blockquote — italic */
    val blockquote: TextStyle,
    /** Lead paragraph — text-xl muted */
    val lead: TextStyle,
    /** Large — text-lg font-semibold */
    val large: TextStyle,
    /** Small — text-sm leading-none font-medium */
    val small: TextStyle,
    /** Muted — text-sm muted-foreground */
    val muted: TextStyle,
    /** Inline code — font-mono text-sm font-semibold */
    val inlineCode: TextStyle,
)

@Composable
fun rememberRobotoFontFamily(): FontFamily {
    val regular    = Font(Res.font.Roboto_VariableFont, FontWeight.Normal)
    val medium     = Font(Res.font.Roboto_VariableFont, FontWeight.Medium)
    val semiBold   = Font(Res.font.Roboto_VariableFont, FontWeight.SemiBold)
    val bold       = Font(Res.font.Roboto_VariableFont, FontWeight.Bold)
    val extraBold  = Font(Res.font.Roboto_VariableFont, FontWeight.ExtraBold)

    val italicRegular  = Font(Res.font.Roboto_Italic_VariableFont, FontWeight.Normal,    FontStyle.Italic)
    val italicMedium   = Font(Res.font.Roboto_Italic_VariableFont, FontWeight.Medium,    FontStyle.Italic)
    val italicSemiBold = Font(Res.font.Roboto_Italic_VariableFont, FontWeight.SemiBold,  FontStyle.Italic)
    val italicBold     = Font(Res.font.Roboto_Italic_VariableFont, FontWeight.Bold,      FontStyle.Italic)
    val italicExtraBold= Font(Res.font.Roboto_Italic_VariableFont, FontWeight.ExtraBold, FontStyle.Italic)

    return remember(
        regular, medium, semiBold, bold, extraBold,
        italicRegular, italicMedium, italicSemiBold, italicBold, italicExtraBold
    ) {
        FontFamily(
            regular, medium, semiBold, bold, extraBold,
            italicRegular, italicMedium, italicSemiBold, italicBold, italicExtraBold
        )
    }
}

fun buildAmethystTypography(fontFamily: FontFamily): AmethystTypography = AmethystTypography(
    h1 = TextStyle(
        fontFamily = fontFamily,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.025).em,
    ),
    h2 = TextStyle(
        fontFamily = fontFamily,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.025).em,
    ),
    h3 = TextStyle(
        fontFamily = fontFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.025).em,
    ),
    h4 = TextStyle(
        fontFamily = fontFamily,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.025).em,
    ),
    p = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
    ),
    blockquote = TextStyle(
        fontFamily = fontFamily,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
    ),
    lead = TextStyle(
        fontFamily = fontFamily,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
    ),
    large = TextStyle(
        fontFamily = fontFamily,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    small = TextStyle(
        fontFamily = fontFamily,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    muted = TextStyle(
        fontFamily = fontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    inlineCode = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

@Composable
fun rememberAmethystTypography(): AmethystTypography {
    val roboto = rememberRobotoFontFamily()
    return remember(roboto) { buildAmethystTypography(roboto) }
}
