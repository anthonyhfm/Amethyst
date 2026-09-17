package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.composeunstyled.Text
import dev.anthonyhfm.amethyst.core.diagnostics.PerformanceDiagnostics
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.time.TimeSource

@Composable
fun PerformanceOverlay(
    modifier: Modifier = Modifier,
) {
    var windowFps by remember { mutableIntStateOf(0) }
    var heavenFps by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        var windowFramesInSample = 0
        var lastSampleMark = TimeSource.Monotonic.markNow()

        while (isActive) {
            withFrameNanos { _ ->
                windowFramesInSample++
                val elapsed = lastSampleMark.elapsedNow()
                val elapsedMs = elapsed.inWholeMilliseconds
                if (elapsedMs >= 500) {
                    val safeElapsedMs = elapsedMs.coerceAtLeast(1L)
                    windowFps = ((windowFramesInSample * 1000.0) / safeElapsedMs).roundToInt()
                    val hFrames = PerformanceDiagnostics.consumeHeavenFrames()
                    heavenFps = ((hFrames * 1000.0) / safeElapsedMs).roundToInt()

                    windowFramesInSample = 0
                    lastSampleMark = TimeSource.Monotonic.markNow()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .zIndex(100_000f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC121216))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.width(115.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Window",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$windowFps FPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Heaven",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$heavenFps FPS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (heavenFps > 0) Color(0xFF68D391) else Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}
