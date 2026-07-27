package dev.anthonyhfm.amethyst.home.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sin

private const val LOGO_VIEWPORT_WIDTH = 623f
private const val LOGO_VIEWPORT_HEIGHT = 482f
private const val LOGO_ASPECT_RATIO = LOGO_VIEWPORT_WIDTH / LOGO_VIEWPORT_HEIGHT

private const val PATH_CIRCLE = "M80.47,326.83C121.6,326.83,154.94,360.18,154.94,401.3C154.94,442.43,121.6,475.77,80.47,475.77C39.34,475.77,6,442.43,6,401.3C6,360.18,39.34,326.83,80.47,326.83z"
private const val PATH_TOP_PILL = "M144.41,183.04L246.14,37.76C269.73,4.07,316.16,-4.12,349.85,19.47C383.54,43.06,391.73,89.5,368.14,123.19L266.42,268.47C242.83,302.16,196.39,310.35,162.7,286.76C129.01,263.17,120.82,216.73,144.41,183.04z"
private const val PATH_BOTTOM_PILL = "M501.39,213.16L603.11,358.44C626.7,392.13,618.51,438.56,584.82,462.15C551.13,485.74,504.7,477.56,481.11,443.87L379.38,298.59C355.79,264.9,363.98,218.46,397.67,194.87C431.36,171.28,477.8,179.47,501.39,213.16z"

@Composable
fun AmethystLoadingLogo(
    progress: Float,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(350, easing = LinearOutSlowInEasing),
        label = "LogoProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val logoPath = remember {
        Path().apply {
            val parser = PathParser()
            addPath(parser.parsePathString(PATH_CIRCLE).toPath())
            addPath(parser.parsePathString(PATH_TOP_PILL).toPath())
            addPath(parser.parsePathString(PATH_BOTTOM_PILL).toPath())
        }
    }

    val height = width / LOGO_ASPECT_RATIO

    Box(
        modifier = modifier.size(width = width, height = height),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = min(this.size.width / LOGO_VIEWPORT_WIDTH, this.size.height / LOGO_VIEWPORT_HEIGHT)
            val dx = (this.size.width - LOGO_VIEWPORT_WIDTH * scale) / 2f
            val dy = (this.size.height - LOGO_VIEWPORT_HEIGHT * scale) / 2f

            translate(left = dx, top = dy) {
                val matrix = Matrix().apply {
                    scale(scale, scale)
                }
                val scaledPath = Path().apply {
                    addPath(logoPath)
                    transform(matrix)
                }

                val scaledWidth = LOGO_VIEWPORT_WIDTH * scale
                val scaledHeight = LOGO_VIEWPORT_HEIGHT * scale

                // 1. Unfilled Outline & Base (Clean Zinc-800)
                drawPath(
                    path = scaledPath,
                    color = Color(0xFF18181B)
                )
                drawPath(
                    path = scaledPath,
                    color = Color(0xFF27272A),
                    style = Stroke(width = 1.5f * scale)
                )

                // 2. Subtle Flat/Micro-Wave Fill
                if (animatedProgress > 0.001f) {
                    val liquidHeight = scaledHeight * animatedProgress
                    val liquidTopY = scaledHeight - liquidHeight
                    val waveAmp = 1.5f * scale // Ultra subtle wave depth

                    val liquidPath = Path().apply {
                        moveTo(-10f, scaledHeight + 10f)
                        lineTo(-10f, liquidTopY)

                        var x = -10f
                        val step = 6f
                        while (x <= scaledWidth + 10f) {
                            val y = liquidTopY + sin((x * 0.018f) + wavePhase) * waveAmp
                            lineTo(x, y)
                            x += step
                        }

                        lineTo(scaledWidth + 10f, scaledHeight + 10f)
                        close()
                    }

                    clipPath(scaledPath) {
                        // Clean Subtle Gradient Fill (Purple-400 to Violet-600)
                        drawPath(
                            path = liquidPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFC084FC),
                                    Color(0xFFA855F7),
                                    Color(0xFF9333EA)
                                ),
                                startY = liquidTopY - waveAmp,
                                endY = scaledHeight
                            )
                        )

                        // Subtle Surface Highlight
                        val crestLine = Path().apply {
                            moveTo(-10f, liquidTopY)
                            var x = -10f
                            val step = 6f
                            while (x <= scaledWidth + 10f) {
                                val y = liquidTopY + sin((x * 0.018f) + wavePhase) * waveAmp
                                lineTo(x, y)
                                x += step
                            }
                        }

                        drawPath(
                            path = crestLine,
                            color = Color(0xFFE9D5FF),
                            style = Stroke(width = 1.5f * scale, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
