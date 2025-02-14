package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
fun KeyframesPlaybackControls() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        IconButton(
            onClick = {

            }
        ) {
            Icon(Icons.Default.SkipPrevious, null)
        }

        IconButton(
            onClick = {

            }
        ) {
            Icon(Icons.Default.PlayArrow, null)
        }

        IconButton(
            onClick = {

            }
        ) {
            Icon(Icons.Default.SkipNext, null)
        }
    }
}