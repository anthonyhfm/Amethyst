package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.twotone.Audiotrack
import androidx.compose.material.icons.twotone.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.AmethystContextMenu
import dev.anthonyhfm.amethyst.ui.components.ContextMenuItem

@Composable
fun AddTrackButton(
    onAddLightsTrack: () -> Unit = {},
    onAddAudioTrack: () -> Unit = {}
) {
    var showDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(56.dp),

        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = {
                showDropdown = true
            }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Track"
            )
        }

        AmethystContextMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) { _, _, _ ->
            ContextMenuItem(
                label = "Midi Track",
                icon = Icons.TwoTone.Lightbulb,
                onClick = {
                    onAddLightsTrack()
                    showDropdown = false
                }
            )
            ContextMenuItem(
                label = "Audio Track",
                icon = Icons.TwoTone.Audiotrack,
                onClick = {
                    onAddAudioTrack()
                    showDropdown = false
                }
            )
        }
    }
}