package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.AutoPlayRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun AutoPlayButtons() {
    Row(
        modifier = Modifier
            .padding(bottom = 24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape),

        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {
                    AutoPlayRepository.startAutoPlay()
                },

            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Start AutoPlay",
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(12.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .height(44.dp)
                .clickable {

                },

            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop AutoPlay",
                modifier = Modifier
                    .clip(CircleShape)
                    .padding(12.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}