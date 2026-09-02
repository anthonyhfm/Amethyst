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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.timeline.data.TimelineAutomationLane
import dev.anthonyhfm.amethyst.timeline.ui.views.TimelineAutomationLaneRowHeight
import dev.anthonyhfm.amethyst.ui.components.primitives.FullShape
import dev.anthonyhfm.amethyst.ui.components.primitives.SmallShape
import dev.anthonyhfm.amethyst.ui.theme.TimelineTheme
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun TrackAutomationLaneCard(
    lane: TimelineAutomationLane,
    label: String,
    valueText: String,
    pointCount: Int,
    selected: Boolean,
    contentColor: Color,
    accentColor: Color,
    activeContainer: Color,
    activeBorder: Color,
    activeContent: Color,
    inactiveContainer: Color,
    inactiveBorder: Color,
    inactiveContent: Color,
    onSelect: () -> Unit,
    onEnabledToggle: () -> Unit,
    onHide: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimelineAutomationLaneRowHeight)
            .clip(SmallShape)
            .background(
                if (selected) {
                    TimelineTheme.palette.automationLaneSurface.copy(alpha = 0.96f)
                } else {
                    TimelineTheme.palette.automationLaneSurface.copy(alpha = 0.82f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    accentColor
                } else {
                    inactiveBorder
                },
                shape = SmallShape
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(FullShape)
                .background(accentColor.copy(alpha = if (lane.enabled) 0.95f else 0.45f))
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Theme[typography][small].copy(
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "$pointCount pts · $valueText",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = Theme[typography][small].copy(
                    color = contentColor.copy(alpha = 0.62f)
                )
            )
        }
        TrackActionPill(
            label = "AUTO",
            active = lane.enabled,
            activeContainer = activeContainer,
            activeBorder = activeBorder,
            activeContent = activeContent,
            inactiveContainer = inactiveContainer,
            inactiveBorder = inactiveBorder,
            inactiveContent = inactiveContent,
            onClick = onEnabledToggle
        )
        TrackChromeChip(
            containerColor = inactiveContainer,
            borderColor = inactiveBorder,
            onClick = onHide
        ) {
            Text(
                text = "×",
                style = Theme[typography][small].copy(
                    color = inactiveContent,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
