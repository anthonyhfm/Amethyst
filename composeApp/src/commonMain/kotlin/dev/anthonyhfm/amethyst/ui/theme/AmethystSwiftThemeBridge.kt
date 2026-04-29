package dev.anthonyhfm.amethyst.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

data class SwiftAmethystPalette(
    val backgroundArgb: Long,
    val foregroundArgb: Long,
    val cardArgb: Long,
    val cardForegroundArgb: Long,
    val secondaryArgb: Long,
    val secondaryForegroundArgb: Long,
    val mutedArgb: Long,
    val mutedForegroundArgb: Long,
    val primaryArgb: Long,
    val primaryForegroundArgb: Long,
    val borderArgb: Long,
    val destructiveArgb: Long,
)

object AmethystSwiftThemeBridge {
    fun palette(darkMode: Boolean): SwiftAmethystPalette {
        val palette = amethystColorPalette(darkMode)

        return SwiftAmethystPalette(
            backgroundArgb = palette.background.toArgbLong(),
            foregroundArgb = palette.foreground.toArgbLong(),
            cardArgb = palette.card.toArgbLong(),
            cardForegroundArgb = palette.cardForeground.toArgbLong(),
            secondaryArgb = palette.secondary.toArgbLong(),
            secondaryForegroundArgb = palette.secondaryForeground.toArgbLong(),
            mutedArgb = palette.muted.toArgbLong(),
            mutedForegroundArgb = palette.mutedForeground.toArgbLong(),
            primaryArgb = palette.primary.toArgbLong(),
            primaryForegroundArgb = palette.primaryForeground.toArgbLong(),
            borderArgb = palette.border.toArgbLong(),
            destructiveArgb = palette.destructive.toArgbLong(),
        )
    }
}

private fun Color.toArgbLong(): Long {
    val alphaChannel = (alpha * 255f).roundToInt().toLong() and 0xFF
    val redChannel = (red * 255f).roundToInt().toLong() and 0xFF
    val greenChannel = (green * 255f).roundToInt().toLong() and 0xFF
    val blueChannel = (blue * 255f).roundToInt().toLong() and 0xFF

    return (alphaChannel shl 24) or
        (redChannel shl 16) or
        (greenChannel shl 8) or
        blueChannel
}
