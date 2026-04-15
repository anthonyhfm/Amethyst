package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.card
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.chart1
import dev.anthonyhfm.amethyst.ui.theme.chart2
import dev.anthonyhfm.amethyst.ui.theme.chart3
import dev.anthonyhfm.amethyst.ui.theme.chart4
import dev.anthonyhfm.amethyst.ui.theme.chart5
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

// ─── Chart Config ──────────────────────────────────────────────────────────────

data class ChartConfigEntry(
    val label: String,
    val color: Color? = null,
)

typealias ChartConfig = Map<String, ChartConfigEntry>

/**
 * Returns the resolved color for a config key, falling back to the default
 * chart palette (chart1–chart5) when no explicit color is set.
 */
@Composable
fun ChartConfig.colorFor(key: String): Color {
    val explicit = this[key]?.color
    if (explicit != null) return explicit

    val palette = listOf(
        Theme[colors][chart1],
        Theme[colors][chart2],
        Theme[colors][chart3],
        Theme[colors][chart4],
        Theme[colors][chart5],
    )
    val index = keys.toList().indexOf(key)
    return if (index >= 0) palette[index % palette.size] else palette[0]
}

// ─── Chart Container ───────────────────────────────────────────────────────────

@Composable
fun ChartContainer(
    config: ChartConfig,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][card])
            .padding(24.dp),
    ) {
        ProvideTextStyle(Theme[typography][p].copy(color = Theme[colors][cardForeground])) {
            content()

            if (showLegend) {
                ChartLegend(
                    config = config,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

// ─── Chart Legend ──────────────────────────────────────────────────────────────

@Composable
fun ChartLegend(
    config: ChartConfig,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        config.entries.forEachIndexed { index, (key, entry) ->
            if (index > 0) {
                Box(Modifier.size(16.dp))
            }
            ChartLegendItem(
                label = entry.label,
                color = config.colorFor(key),
            )
        }
    }
}

@Composable
private fun ChartLegendItem(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
        )
    }
}

// ─── Chart Tooltip ─────────────────────────────────────────────────────────────

data class ChartTooltipEntry(
    val name: String,
    val value: String,
    val color: Color,
)

@Composable
fun ChartTooltipContent(
    label: String,
    entries: List<ChartTooltipEntry>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][background])
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = Theme[typography][small],
            color = Theme[colors][foreground],
        )

        entries.forEach { entry ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(entry.color),
                )
                Text(
                    text = entry.name,
                    style = Theme[typography][small],
                    color = Theme[colors][mutedForeground],
                )
                Box(Modifier.weight(1f))
                Text(
                    text = entry.value,
                    style = Theme[typography][small],
                    color = Theme[colors][foreground],
                )
            }
        }
    }
}

// ─── Bar Chart ─────────────────────────────────────────────────────────────────

data class ChartDataPoint(
    val label: String,
    val values: Map<String, Float>,
)

@Composable
fun BarChart(
    data: List<ChartDataPoint>,
    config: ChartConfig,
    modifier: Modifier = Modifier,
    barRadius: Float = 4f,
    barSpacing: Float = 4f,
    showGrid: Boolean = true,
    gridLineCount: Int = 4,
    onHover: ((index: Int?) -> Unit)? = null,
) {
    val gridColor = Theme[colors][border]
    val labelColor = Theme[colors][mutedForeground]
    val labelStyle = Theme[typography][small]

    val resolvedColors = config.mapValues { (key, _) ->
        config.colorFor(key)
    }
    val dataKeys = config.keys.toList()

    val maxValue = data.maxOfOrNull { point ->
        point.values.values.maxOrNull() ?: 0f
    } ?: 1f

    var hoveredIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        // X-axis labels
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(data.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Move -> {
                                    val x = event.changes.firstOrNull()?.position?.x ?: 0f
                                    val groupWidth = size.width.toFloat() / data.size
                                    val idx = (x / groupWidth).toInt().coerceIn(0, data.size - 1)
                                    if (hoveredIndex != idx) {
                                        hoveredIndex = idx
                                        onHover?.invoke(idx)
                                    }
                                }
                                PointerEventType.Exit -> {
                                    hoveredIndex = null
                                    onHover?.invoke(null)
                                }
                            }
                        }
                    }
                },
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val groupCount = data.size
            val keyCount = dataKeys.size

            // Draw horizontal grid lines
            if (showGrid) {
                for (i in 0..gridLineCount) {
                    val y = canvasHeight - (canvasHeight * i / gridLineCount)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f,
                    )
                }
            }

            // Draw bars
            val groupWidth = canvasWidth / groupCount
            val totalBarSpacing = barSpacing * (keyCount - 1)
            val barWidth = (groupWidth * 0.6f - totalBarSpacing) / keyCount

            data.forEachIndexed { groupIndex, point ->
                val groupX = groupIndex * groupWidth + groupWidth * 0.2f

                dataKeys.forEachIndexed { keyIndex, key ->
                    val value = point.values[key] ?: 0f
                    val barHeight = (value / maxValue) * canvasHeight
                    val x = groupX + keyIndex * (barWidth + barSpacing)
                    val y = canvasHeight - barHeight
                    val color = resolvedColors[key] ?: gridColor

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barRadius, barRadius),
                    )
                }
            }
        }

        // X-axis labels row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            data.forEach { point ->
                Text(
                    text = point.label,
                    style = labelStyle,
                    color = labelColor,
                )
            }
        }
    }
}

// ─── Line Chart ────────────────────────────────────────────────────────────────

@Composable
fun LineChart(
    data: List<ChartDataPoint>,
    config: ChartConfig,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    showDots: Boolean = true,
    dotRadius: Float = 4f,
    showGrid: Boolean = true,
    gridLineCount: Int = 4,
    showArea: Boolean = false,
    onHover: ((index: Int?) -> Unit)? = null,
) {
    val gridColor = Theme[colors][border]
    val labelColor = Theme[colors][mutedForeground]
    val bgColor = Theme[colors][card]
    val labelStyle = Theme[typography][small]

    val resolvedColors = config.mapValues { (key, _) ->
        config.colorFor(key)
    }
    val dataKeys = config.keys.toList()

    val maxValue = data.maxOfOrNull { point ->
        point.values.values.maxOrNull() ?: 0f
    } ?: 1f

    var hoveredIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(data.size) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Move -> {
                                    val x = event.changes.firstOrNull()?.position?.x ?: 0f
                                    val step = if (data.size > 1) {
                                        size.width.toFloat() / (data.size - 1)
                                    } else {
                                        size.width.toFloat()
                                    }
                                    val idx = ((x + step / 2) / step).toInt()
                                        .coerceIn(0, data.size - 1)
                                    if (hoveredIndex != idx) {
                                        hoveredIndex = idx
                                        onHover?.invoke(idx)
                                    }
                                }
                                PointerEventType.Exit -> {
                                    hoveredIndex = null
                                    onHover?.invoke(null)
                                }
                            }
                        }
                    }
                },
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Draw horizontal grid lines
            if (showGrid) {
                for (i in 0..gridLineCount) {
                    val y = canvasHeight - (canvasHeight * i / gridLineCount)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(canvasWidth, y),
                        strokeWidth = 1f,
                    )
                }
            }

            if (data.isEmpty()) return@Canvas

            val stepX = if (data.size > 1) canvasWidth / (data.size - 1) else canvasWidth / 2

            // Draw each data series
            dataKeys.forEach { key ->
                val color = resolvedColors[key] ?: gridColor
                val points = data.mapIndexed { index, point ->
                    val x = index * stepX
                    val value = point.values[key] ?: 0f
                    val y = canvasHeight - (value / maxValue) * canvasHeight
                    Offset(x, y)
                }

                // Draw filled area under the line
                if (showArea && points.size >= 2) {
                    val areaPath = Path().apply {
                        moveTo(points.first().x, canvasHeight)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, canvasHeight)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        color = color.copy(alpha = 0.1f),
                    )
                }

                // Draw connecting lines
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = color,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }

                // Draw dots
                if (showDots) {
                    points.forEach { point ->
                        drawCircle(
                            color = color,
                            radius = dotRadius,
                            center = point,
                        )
                        drawCircle(
                            color = bgColor,
                            radius = dotRadius - strokeWidth,
                            center = point,
                        )
                    }
                }
            }

            // Draw hover indicator line
            hoveredIndex?.let { idx ->
                val x = idx * stepX
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1f,
                )
            }
        }

        // X-axis labels row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            data.forEach { point ->
                Text(
                    text = point.label,
                    style = labelStyle,
                    color = labelColor,
                )
            }
        }
    }
}
