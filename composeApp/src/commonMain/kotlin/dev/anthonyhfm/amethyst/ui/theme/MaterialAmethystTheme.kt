package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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

@Composable
fun ComposeAmethystTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val materialTypography = rememberAmethystMaterialTypography()
    val colorScheme = remember(darkMode) { amethystMaterialColorScheme(darkMode) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = materialTypography,
    ) {
        Surface(
            color = colorScheme.background,
            contentColor = colorScheme.onBackground,
        ) {
            AmethystTheme(
                darkMode = darkMode,
                content = content,
            )
        }
    }
}
