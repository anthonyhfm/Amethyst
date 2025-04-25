package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FrameControl(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
        ) {
            IconButton(
                onClick = {

                }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Skip to previous frame"
                )
            }

            IconButton(
                onClick = {

                }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play"
                )
            }

            IconButton(
                onClick = {

                }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip to next frame"
                )
            }
        }
    }
}