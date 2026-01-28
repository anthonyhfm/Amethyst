package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ExpandCircleDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun WorkspaceMode(
    mode: WorkspaceContract.WorkspaceMode,
) {
    val compactMode = platform !is Platform.Desktop

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

    if (compactMode) {
        CompactLayout(mode, selectableModes)
    } else {
        LargeLayout(mode, selectableModes)
    }
}

@Composable
fun LargeLayout(
    mode: WorkspaceContract.WorkspaceMode,
    selectableModes: List<WorkspaceModePickerItem>,
) {
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

        selectableModes.forEach { item ->
            val isSelected = when {
                mode is WorkspaceContract.WorkspaceMode.Layout && item.mode is WorkspaceContract.WorkspaceMode.Layout -> true
                mode is WorkspaceContract.WorkspaceMode.Preview && item.mode is WorkspaceContract.WorkspaceMode.Preview -> true
                mode is WorkspaceContract.WorkspaceMode.LightsChain && item.mode is WorkspaceContract.WorkspaceMode.LightsChain -> true
                mode is WorkspaceContract.WorkspaceMode.SamplingChain && item.mode is WorkspaceContract.WorkspaceMode.SamplingChain -> true
                mode is WorkspaceContract.WorkspaceMode.Timeline && item.mode is WorkspaceContract.WorkspaceMode.Timeline -> true
                else -> false
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .height(44.dp)
                    .background(
                        animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            }
                        ).value
                    )
                    .clickable {
                        WorkspaceRepository.switchMode(item.mode)
                    },

                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.icon ?: Icons.Default.Close,
                    contentDescription = item.text,
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(12.dp)
                        .size(22.dp),
                    tint = animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ).value
                )

                AnimatedVisibility(
                    visible = isSelected,
                ) {
                    Text(
                        text = item.text,
                        color = animateColorAsState(
                            targetValue = if (isSelected) {
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

@Composable
fun CompactLayout(
    mode: WorkspaceContract.WorkspaceMode,
    selectableModes: List<WorkspaceModePickerItem>,
) {
    var expanded by remember { mutableStateOf(false) }

    Popup {
        Box {
            Column(
                modifier = Modifier
                    .padding(start = 54.dp)
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(23.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), RoundedCornerShape(22.dp))
            ) {
                selectableModes.find { item ->
                    when {
                        mode is WorkspaceContract.WorkspaceMode.Layout && item.mode is WorkspaceContract.WorkspaceMode.Layout -> true
                        mode is WorkspaceContract.WorkspaceMode.Preview && item.mode is WorkspaceContract.WorkspaceMode.Preview -> true
                        mode is WorkspaceContract.WorkspaceMode.LightsChain && item.mode is WorkspaceContract.WorkspaceMode.LightsChain -> true
                        mode is WorkspaceContract.WorkspaceMode.SamplingChain && item.mode is WorkspaceContract.WorkspaceMode.SamplingChain -> true
                        mode is WorkspaceContract.WorkspaceMode.Timeline && item.mode is WorkspaceContract.WorkspaceMode.Timeline -> true
                        else -> false
                    }
                }?.let { current ->
                    Row(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .height(44.dp)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary),

                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    expanded = !expanded
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = current.icon ?: Icons.Default.Close,
                                contentDescription = current.text,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .padding(12.dp)
                                    .size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )

                            Text(
                                text = current.text,
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize,
                                ),
                                modifier = Modifier.padding(end = 16.dp),
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        selectableModes
                            .filterNot { item ->
                                when {
                                    mode is WorkspaceContract.WorkspaceMode.Layout && item.mode is WorkspaceContract.WorkspaceMode.Layout -> true
                                    mode is WorkspaceContract.WorkspaceMode.Preview && item.mode is WorkspaceContract.WorkspaceMode.Preview -> true
                                    mode is WorkspaceContract.WorkspaceMode.LightsChain && item.mode is WorkspaceContract.WorkspaceMode.LightsChain -> true
                                    mode is WorkspaceContract.WorkspaceMode.SamplingChain && item.mode is WorkspaceContract.WorkspaceMode.SamplingChain -> true
                                    mode is WorkspaceContract.WorkspaceMode.Timeline && item.mode is WorkspaceContract.WorkspaceMode.Timeline -> true
                                    else -> false
                                }
                            }
                            .forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(22.dp))
                                        .height(44.dp)
                                        .fillMaxWidth()
                                        .clickable {
                                            WorkspaceRepository.switchMode(item.mode)
                                            expanded = false
                                        }
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = item.icon ?: Icons.Default.Close,
                                        contentDescription = item.text,
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = item.text,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            lineHeight = MaterialTheme.typography.bodyLarge.fontSize,
                                        ),
                                    )

                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                    }
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