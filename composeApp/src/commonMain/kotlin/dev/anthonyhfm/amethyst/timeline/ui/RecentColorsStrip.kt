package dev.anthonyhfm.amethyst.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primary
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentColorsStrip(
    recentColors: List<Triple<Float, Float, Float>>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    if (recentColors.isEmpty()) return

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        maxItemsInEachRow = 8
    ) {
        recentColors.take(16).forEach { (r, g, b) ->
            val swatchColor = Color(r, g, b)
            val isSelected = abs(r - selectedColor.red) < 0.01f &&
                abs(g - selectedColor.green) < 0.01f &&
                abs(b - selectedColor.blue) < 0.01f
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(swatchColor)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Theme[colors][primary] else Theme[colors][border],
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onColorSelected(swatchColor) }
            )
        }
    }
}
