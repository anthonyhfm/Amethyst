package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandCircleDown
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
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceController
import org.koin.compose.koinInject

@Composable
fun WorkspaceMode(
    mode: WorkspaceContract.WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    var showModeSwitch: Boolean by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .height(42.dp)
            .background(
                if (mode.selectable) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            )
            .clickable {
                showModeSwitch = true
            }
            .padding(12.dp),

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (mode.selectable) {
            Icon(
                imageVector = Icons.Default.ExpandCircleDown,
                contentDescription = null
            )
        } else {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null
            )
        }

        Text(
            text = mode.displayName,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyMedium.fontSize,
            modifier = Modifier
                .padding(end = 4.dp)
        )
    }

    ModeSwitchDialog(
        expanded = showModeSwitch,
        onDismiss = {
            showModeSwitch = false
        },
        onSelect = {
            onEvent(WorkspaceContract.Event.ChangeWorkspaceMode(it))
            showModeSwitch = false
        }
    )
}

@Composable
private fun ModeSwitchDialog(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (WorkspaceContract.WorkspaceMode) -> Unit
) {
    val selectableModes = listOf(
        WorkspaceContract.WorkspaceMode.Layout(),
        WorkspaceContract.WorkspaceMode.Preview(),
        WorkspaceContract.WorkspaceMode.Chain(),
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        selectableModes.forEach {
            DropdownMenuItem(
                text = {
                    Text(it.displayName)
                },
                onClick = {
                    onSelect(it)
                }
            )
        }
    }
}