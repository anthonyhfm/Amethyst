package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import org.koin.compose.koinInject

@Composable
fun WorkspaceMode(
    mode: WorkspaceContract.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    val selectableModes = listOf(
        WorkspaceModePickerItem(
            mode = WorkspaceContract.WorkspaceMode.Layout(),
            text = "Layout",
            icon = Icons.Default.Draw
        ),
        WorkspaceModePickerItem(
            mode = WorkspaceContract.WorkspaceMode.Preview(),
            text = "Preview",
            icon = Icons.Default.Preview
        ),
        WorkspaceModePickerItem(
            mode = WorkspaceContract.WorkspaceMode.LightsChain(),
            text = "Lights Chain",
            icon = Icons.Default.Lightbulb
        ),
        WorkspaceModePickerItem(
            mode = WorkspaceContract.WorkspaceMode.SamplingChain(),
            text = "Sampling Chain",
            icon = Icons.Default.MusicNote
        ),
        WorkspaceModePickerItem(
            mode = WorkspaceContract.WorkspaceMode.Timeline(),
            text = "Timeline",
            icon = Icons.Default.Timeline
        )
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape)
            .padding(2.dp),

        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = !mode.selectable,
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.error)
                    .clickable {
                        WorkspaceRepository.switchToPreviousMode()
                    },

                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = mode.displayName,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(12.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.onError
                )

                Text(
                    text = mode.displayName,
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize,
                    ),
                    modifier = Modifier
                        .padding(end = 16.dp),
                )
            }
        }

        selectableModes.forEach { it ->
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .height(44.dp)
                    .background(
                        animateColorAsState(
                            targetValue = if (mode == it.mode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ).value
                    )
                    .clickable {
                        WorkspaceRepository.switchMode(it.mode)
                    },

                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = it.icon ?: Icons.Default.Close,
                    contentDescription = it.text,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(12.dp)
                        .size(22.dp),
                    tint = animateColorAsState(
                        targetValue = if (mode == it.mode) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ).value
                )

                AnimatedVisibility(
                    visible = mode == it.mode,
                ) {
                    Text(
                        text = it.text,
                        color = animateColorAsState(
                            targetValue = if (mode == it.mode) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ).value,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        modifier = Modifier
                            .padding(end = 16.dp),
                    )
                }
            }
        }
    }
}

data class WorkspaceModePickerItem(
    val mode: WorkspaceContract.WorkspaceMode,
    val text: String,
    val icon: ImageVector? = null,
)
