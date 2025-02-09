package dev.anthonyhfm.amethyst.editor.tracks.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun CreateTrackButton(onCreate: (CreateTrackType) -> Unit) {
    var menuVisible: Boolean by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .width(160.dp)
                .height(80.dp)
                .clickable {
                    menuVisible = true
                }
                .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(0.4f), RoundedCornerShape(6.dp)),

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null
            )

            Text(
                text = "New Track",
                style = MaterialTheme.typography.labelLarge,
                lineHeight = MaterialTheme.typography.labelLarge.fontSize
            )
        }

        DropdownMenu(
            expanded = menuVisible,
            onDismissRequest = {
                menuVisible = false
            }
        ) {
            DropdownMenuItem(
                text = {
                    Text("Audio-Track")
                },
                leadingIcon = {
                    Icon(Icons.Default.Audiotrack, null)
                },
                onClick = {
                    onCreate(CreateTrackType.Audio)
                }
            )

            DropdownMenuItem(
                text = {
                    Text("Effect-Track")
                },
                leadingIcon = {
                    Icon(Icons.Default.Lightbulb, null)
                },
                onClick = {
                    onCreate(CreateTrackType.Effect)
                }
            )
        }
    }
}

enum class CreateTrackType {
    Audio,
    Effect
}