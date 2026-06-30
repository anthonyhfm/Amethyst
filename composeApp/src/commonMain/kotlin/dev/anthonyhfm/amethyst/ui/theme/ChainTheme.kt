package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken

val chainColorTokens = ThemeProperty<Color>("chain_colors")

val chainCanvas = ThemeToken<Color>("chain_canvas")
val chainSurface = ThemeToken<Color>("chain_surface")
val chainSurfaceRaised = ThemeToken<Color>("chain_surface_raised")
val chainBorder = ThemeToken<Color>("chain_border")

internal val lightChainColorMap = mapOf(
    chainCanvas to Color(0xFFF4F6FA),
    chainSurface to Color(0xFFFBFCFE),
    chainSurfaceRaised to Color(0xFFFFFFFF),
    chainBorder to Color(0xFFD8DEE8),
)

internal val darkChainColorMap = mapOf(
    chainCanvas to Color(0xFF1C1F23),
    chainSurface to Color(0xFF13141F),
    chainSurfaceRaised to Color(0xFF292E3D),
    chainBorder to Color(0xFF282936),
)
