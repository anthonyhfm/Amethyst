package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val MATERIAL_AMETHYST_THEME = amethystMaterialColorScheme(darkMode = true)

fun AmethystTypography.toMaterialTypography(): Typography {
    return Typography(
        displayLarge = h1,
        displayMedium = h2,
        displaySmall = h3,
        headlineLarge = h2,
        headlineMedium = h3,
        headlineSmall = h4,
        titleLarge = h4,
        titleMedium = large,
        titleSmall = small,
        bodyLarge = p,
        bodyMedium = p,
        bodySmall = muted,
        labelLarge = small,
        labelMedium = small,
        labelSmall = muted,
    )
}

@Composable
fun rememberAmethystMaterialTypography(): Typography {
    val typography = rememberAmethystTypography()
    return remember(typography) { typography.toMaterialTypography() }
}

fun amethystMaterialColorScheme(darkMode: Boolean = true): ColorScheme {
    // Amethyst is always in our dark theme on Android
    val palette = AmethystDarkPalette

    return darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.primaryForeground,
        primaryContainer = palette.accent,
        onPrimaryContainer = palette.accentForeground,
        inversePrimary = palette.ring,
        secondary = palette.secondary,
        onSecondary = palette.secondaryForeground,
        secondaryContainer = palette.muted,
        onSecondaryContainer = palette.secondaryForeground,
        tertiary = palette.selectionSurface,
        onTertiary = palette.selectionForeground,
        tertiaryContainer = palette.chart2,
        onTertiaryContainer = palette.foreground,
        background = palette.background,
        onBackground = palette.foreground,
        surface = palette.card,
        onSurface = palette.cardForeground,
        surfaceVariant = palette.muted,
        onSurfaceVariant = palette.mutedForeground,
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = Color(0xFF111827),
        surfaceContainer = palette.muted,
        surfaceContainerHigh = palette.secondary,
        surfaceContainerHighest = Color(0xFF374151),
        surfaceDim = palette.background,
        surfaceBright = palette.secondary,
        outline = palette.border,
        outlineVariant = palette.input,
        error = palette.destructive,
        onError = palette.destructiveForeground,
        errorContainer = palette.destructive.copy(alpha = 0.75f),
        onErrorContainer = palette.destructiveForeground,
        inverseSurface = palette.foreground,
        inverseOnSurface = palette.background,
        surfaceTint = palette.primary,
        scrim = Color.Black.copy(alpha = 0.7f),
    )
}

@Composable
actual fun PlatformMaterialTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit,
) {
    val materialTypography = rememberAmethystMaterialTypography()
    // Always use our Amethyst dark theme on Android
    val colorScheme = remember { amethystMaterialColorScheme(darkMode = true) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = materialTypography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
        ) {
            content()
        }
    }
}
