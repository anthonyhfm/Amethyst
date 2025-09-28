package dev.anthonyhfm.amethyst.ui.launchpad.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.engine.heaven.mix
import kotlin.math.max
import kotlin.math.min

@Composable
fun GenericLaunchpadButton(
    effect: RawLEDUpdate = RawLEDUpdate(0, Color.Black),
    sizeModifier: Modifier,
    enableLightSpot: Boolean = true,
    shape: Shape = RoundedCornerShape(10)
) {
    val backgroundColor = computeColor(effect)

    Canvas(
        modifier = sizeModifier
            .clip(shape)
            .background(backgroundColor)
            .innerShadow(
                shape = RectangleShape,
                shadow = Shadow(
                    radius = 10.dp,
                    spread = 2.dp,
                    color = darkenColor(effect.color, 0.8f).copy(0.6f),
                )
            )
    ) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = size.minDimension / 2f

        if (enableLightSpot) {
            val src = effect.color
            val srcLuma = 0.2126f * src.red + 0.7152f * src.green + 0.0722f * src.blue
            if (srcLuma > 0.02f) {
                val spotBase = (srcLuma * 1.1f).coerceIn(0f, 1f)
                val coreAlpha = 0.65f * spotBase + 0.10f
                val tintAlpha = (coreAlpha * 0.95f).coerceIn(0f, 1f)

                val (eh, es, el) = rgbToHsl(src.red, src.green, src.blue)
                val tintRgb = hslToRgb(eh, (es * 0.6f).coerceIn(0f, 1f), el)
                val tintColor = Color(tintRgb.first, tintRgb.second, tintRgb.third, tintAlpha)

                val spotBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = coreAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 0.75f
                )
                drawCircle(brush = spotBrush, radius = radius * 0.75f, center = center)
            }
        }
    }
}

private fun computeColor(effectData: RawLEDUpdate): Color {
    val background = Color(91, 91, 91)
    val base = effectData.color.mix(background)

    val luma = 0.2126f * base.red + 0.7152f * base.green + 0.0722f * base.blue
    val darkThreshold = 0.08f
    if (luma <= darkThreshold) return base

    val t = ((luma - darkThreshold) / (1f - darkThreshold)).coerceIn(0f, 1f)
    val curve = t * t

    val brightenMax = 0.28f
    val brighten = curve * brightenMax
    val rBright = (base.red + (1f - base.red) * brighten).coerceIn(0f, 1f)
    val gBright = (base.green + (1f - base.green) * brighten).coerceIn(0f, 1f)
    val bBright = (base.blue + (1f - base.blue) * brighten).coerceIn(0f, 1f)

    val (_, sOrig, _) = rgbToHsl(base.red, base.green, base.blue)
    val (h, s, l) = rgbToHsl(rBright, gBright, bBright)

    val baseSatBoost = 0.28f
    val maxSatBoost = 3.5f
    var satAmount = baseSatBoost + curve * (maxSatBoost - baseSatBoost)

    val washedScale = (1f - sOrig)
    satAmount *= (1f + washedScale * washedScale * 1.25f)

    val satThreshold = 0.06f
    if (sOrig <= satThreshold) satAmount *= 0.12f

    val satScale = 0.26f
    val addFactor = (satAmount * satScale).coerceAtMost(1.4f)
    val newS = (s + (1f - s) * addFactor).coerceIn(0f, 1f)

    val contrastStrength = 0.22f
    val contrastMultiplier = 1f + contrastStrength * curve
    var newL = ((l - 0.5f) * contrastMultiplier + 0.5f).coerceIn(0f, 1f)

    val highlightBoost = 0.07f
    if (newL > 0.5f) {
        newL = (newL + (1f - newL) * highlightBoost * curve).coerceIn(0f, 1f)
    }

    val (rFinal, gFinal, bFinal) = hslToRgb(h, newS, newL)
    return Color(rFinal.coerceIn(0f,1f), gFinal.coerceIn(0f,1f), bFinal.coerceIn(0f,1f), 1f)
}

private fun rgbToHsl(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
    val max = max(r, max(g, b))
    val min = min(r, min(g, b))
    val l = (max + min) / 2f
    if (max == min) return Triple(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return Triple(h % 1f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hslToRgb(hIn: Float, s: Float, l: Float): Triple<Float, Float, Float> {
    val h = (hIn % 1f + 1f) % 1f
    if (s == 0f) return Triple(l, l, l)
    val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
    val p = 2f * l - q
    val hk = h
    val tR = (hk + 1f / 3f)
    val tG = hk
    val tB = (hk - 1f / 3f)
    fun f(t: Float): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        return when {
            tt < 1f / 6f -> p + (q - p) * 6f * tt
            tt < 1f / 2f -> q
            tt < 2f / 3f -> p + (q - p) * (2f / 3f - tt) * 6f
            else -> p
        }
    }
    return Triple(f(tR), f(tG), f(tB))
}

private fun darkenColor(c: Color, factor: Float): Color {
    val (h, s, l) = rgbToHsl(c.red, c.green, c.blue)
    val newL = (l * factor).coerceIn(0f, 1f)
    val (r, g, b) = hslToRgb(h, s, newL)
    return Color(r, g, b, c.alpha)
}
