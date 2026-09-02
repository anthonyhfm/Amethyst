package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.large
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.typography

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentColorsRow(
    colors: List<Triple<Float, Float, Float>>,
    selected: Triple<Float, Float, Float>,
    onPick: (Color) -> Unit
) {
    if (colors.isEmpty()) return

    Text("Recent Colors", Modifier.padding(bottom = 4.dp), style = Theme[typography][large].copy(color = Theme[dev.anthonyhfm.amethyst.ui.theme.colors][foreground]))

    Box(
        modifier = Modifier
            .fillMaxWidth(),

        contentAlignment = Alignment.Center
    ) {
        FlowRow(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { rgb ->
                val isSelected = rgb == selected
                val color = Color(rgb.first, rgb.second, rgb.third)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Theme[dev.anthonyhfm.amethyst.ui.theme.colors][primary] else Theme[dev.anthonyhfm.amethyst.ui.theme.colors][border],
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onPick(color) }
                )
            }
        }
    }
}

