package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class SamplingThemeAccessibilityTest {
    @Test
    fun samplingTextTokensMeetContrastInLightAndDarkThemes() {
        listOf("light" to AmethystLightPalette, "dark" to AmethystDarkPalette).forEach { (name, palette) ->
            assertTrue(contrastRatio(palette.foreground, palette.background) >= 4.5, "$name foreground")
            assertTrue(contrastRatio(palette.mutedForeground, palette.background) >= 4.5, "$name muted text")
            assertTrue(contrastRatio(palette.popoverForeground, palette.popover) >= 4.5, "$name popover")
            assertTrue(contrastRatio(palette.selectionForeground, palette.selectionSurface) >= 4.5, "$name selection")
        }
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val light = max(first.relativeLuminance(), second.relativeLuminance())
        val dark = min(first.relativeLuminance(), second.relativeLuminance())
        return (light + 0.05) / (dark + 0.05)
    }

    private fun Color.relativeLuminance(): Double =
        channel(red) * 0.2126 + channel(green) * 0.7152 + channel(blue) * 0.0722

    private fun channel(value: Float): Double = if (value <= 0.04045f) {
        value / 12.92
    } else {
        ((value + 0.055) / 1.055).pow(2.4)
    }
}
