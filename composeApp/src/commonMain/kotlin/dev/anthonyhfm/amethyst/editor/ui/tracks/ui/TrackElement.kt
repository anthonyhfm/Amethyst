package dev.anthonyhfm.amethyst.editor.ui.tracks.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.tracks.Track

@Composable
fun TrackElement(
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
            modifier = Modifier
                .padding(12.dp)
        )
    }
}