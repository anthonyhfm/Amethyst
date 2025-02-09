package dev.anthonyhfm.amethyst.editor.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.project.ProjectDeviceConfig
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import dev.anthonyhfm.amethyst.core.data.tracks.Track

@Composable
fun TrackElement(
    deviceConfigs: List<ProjectDeviceConfig>,
    onChangeDeviceConfig: (Int) -> Unit,
    selected: Boolean,
    onSelect: () -> Unit,
    track: Track
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .width(160.dp)
            .height(80.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
            .clickable {
                onSelect()
            }
            .padding(12.dp),

        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleSmall,
            lineHeight = MaterialTheme.typography.titleSmall.fontSize,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )

        if (track is EffectTrack) {
            var visibleDropdown: Boolean by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(0.8f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable {
                        visibleDropdown = true
                    },

                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deviceConfigs.getOrNull(track.projectDeviceIndex ?: -1)?.name ?: "Select",
                    style = MaterialTheme.typography.labelMedium,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                )

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .rotate(90f)
                )

                DropdownMenu(
                    expanded = visibleDropdown,
                    onDismissRequest = {
                        visibleDropdown = false
                    }
                ) {
                    deviceConfigs.forEachIndexed { i, it ->
                        DropdownMenuItem(
                            text = {
                                Text(it.name)
                            },
                            onClick = {
                                onChangeDeviceConfig(i)

                                visibleDropdown = false
                            }
                        )
                    }
                }
            }
        }
    }
}