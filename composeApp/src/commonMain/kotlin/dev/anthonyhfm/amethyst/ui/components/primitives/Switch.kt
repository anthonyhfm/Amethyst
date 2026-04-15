package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.composeunstyled.ToggleSwitch
import com.composeunstyled.theme.NoIndication
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.primary

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        if (checked) Theme[colors][primary] else Theme[colors][input]
    )

    ToggleSwitch(
        toggled = checked,
        onToggled = onCheckedChange,
        modifier = modifier
            .size(width = 44.dp, height = 24.dp)
            .alpha(if (enabled) 1f else 0.5f),
        enabled = enabled,
        shape = FullShape,
        backgroundColor = trackColor,
        contentPadding = PaddingValues(2.dp),
        indication = NoIndication,
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Theme[colors][background], CircleShape)
            )
        }
    )
}
