package dev.anthonyhfm.amethyst.ui.components.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.FlatDial
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Checkbox
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.workspace.data.ParameterMapping
import dev.anthonyhfm.amethyst.workspace.data.ParameterMappingMode
import kotlin.math.roundToInt

@Composable
fun ParameterMappingDialog(
    mapping: ParameterMapping,
    macroLabel: String,
    targetLabel: String,
    onSave: (ParameterMapping) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogState = rememberDialogState(initiallyVisible = true)
    var draft by remember(mapping) { mutableStateOf(mapping) }

    AlertDialog(
        state = dialogState,
        onDismiss = onDismiss,
    ) {
        AlertDialogHeader {
            AlertDialogTitle("Edit macro mapping")
            AlertDialogDescription("$macroLabel → $targetLabel")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FlatDial(
                type = DialType.Continuous,
                title = "Minimum",
                text = "${(draft.minimum * 100f).roundToInt()}%",
                value = draft.minimum,
                onValueChange = { value ->
                    draft = draft.copy(minimum = value.coerceAtMost(draft.maximum))
                },
                isAutomatable = false,
            )
            FlatDial(
                type = DialType.Continuous,
                title = "Maximum",
                text = "${(draft.maximum * 100f).roundToInt()}%",
                value = draft.maximum,
                onValueChange = { value ->
                    draft = draft.copy(maximum = value.coerceAtLeast(draft.minimum))
                },
                isAutomatable = false,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mapping mode")
            Select(
                value = draft.mode.name,
                options = ParameterMappingMode.entries.map { it.name },
                triggerHeight = 44.dp,
                onValueChange = { draft = draft.copy(mode = ParameterMappingMode.valueOf(it)) },
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = draft.inverted,
                onCheckedChange = { draft = draft.copy(inverted = it) },
            )
            Text(if (draft.inverted) "Inverted · On" else "Inverted · Off")
        }

        AlertDialogFooter {
            AlertDialogCancel(onClick = onDismiss) { Text("Cancel") }
            AlertDialogAction(onClick = {
                onSave(draft)
                onDismiss()
            }) { Text("Save mapping") }
        }
    }
}
