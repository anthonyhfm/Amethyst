package dev.anthonyhfm.amethyst.timeline.ui.components.track

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.composeunstyled.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun TrackToggleChip(
    label: String,
    active: Boolean,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveBorder: Color,
    inactiveContent: Color,
    onClick: () -> Unit
) {
    TrackChromeChip(
        containerColor = if (active) activeContainer else inactiveContainer,
        borderColor = if (active) activeBorder else inactiveBorder,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = Theme[typography][small].copy(
                color = if (active) activeContent else inactiveContent,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
fun TrackActionPill(
    label: String,
    active: Boolean,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveBorder: Color,
    inactiveContent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(SmallShape)
            .background(if (active) activeContainer else inactiveContainer)
            .border(1.dp, if (active) activeBorder else inactiveBorder, SmallShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = Theme[typography][small].copy(
                color = if (active) activeContent else inactiveContent,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
fun TrackChromeChip(
    containerColor: Color,
    borderColor: Color,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val modifier = Modifier
        .size(24.dp)
        .clip(SmallShape)
        .background(containerColor)
        .border(1.dp, borderColor, SmallShape)
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun TrackHierarchyInset(
    nestingLevel: Int,
    color: Color
) {
    if (nestingLevel <= 0) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((nestingLevel * 12).dp))
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(1.dp)
                .background(color.copy(alpha = 0.55f))
        )
    }
}
