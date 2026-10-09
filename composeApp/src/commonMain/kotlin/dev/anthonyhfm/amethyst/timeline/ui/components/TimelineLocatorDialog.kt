package dev.anthonyhfm.amethyst.timeline.ui.components

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.timeline.data.TimelineLocator
import dev.anthonyhfm.amethyst.timeline.locators.TimelineLocatorRepository
import dev.anthonyhfm.amethyst.ui.components.primitives.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TimelineLocatorDialog(
    locator: TimelineLocator,
    onDismiss: () -> Unit,
) {
    val state = rememberDialogState(initiallyVisible = true)
    var name by remember(locator.id) { mutableStateOf(value = locator.name) }
    var beatText by remember(locator.id) { mutableStateOf(value = (locator.beat + 1.0).toString()) }
    val beat = beatText.replace(oldChar = ',', newChar = '.').toDoubleOrNull()?.minus(other = 1.0)
    val valid = name.isNotBlank() && beat != null && beat.isFinite() && beat >= 0.0

    Dialog(state = state, onDismiss = onDismiss) {
        DialogContent {
            DialogTitle(text = stringResource(resource = Res.string.locators_edit))
            Text(text = stringResource(resource = Res.string.locators_name))
            Input(value = name, onValueChange = { name = it })
            Text(text = stringResource(resource = Res.string.locators_beat))
            Input(value = beatText, onValueChange = { beatText = it })
            DialogDescription(text = stringResource(resource = Res.string.locators_help))
            if (!valid) {
                Text(text = stringResource(resource = Res.string.locators_invalid))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                Button(
                    onClick = {
                        TimelineLocatorRepository.jump(id = locator.id)
                        onDismiss()
                    },
                    variant = ButtonVariant.Secondary,
                ) {
                    Text(text = stringResource(resource = Res.string.locators_jump))
                }
                Button(
                    onClick = {
                        TimelineLocatorRepository.controller.delete(id = locator.id)
                        onDismiss()
                    },
                    variant = ButtonVariant.Destructive,
                ) {
                    Text(text = stringResource(resource = Res.string.locators_delete))
                }
            }
            DialogFooter {
                Button(onClick = onDismiss, variant = ButtonVariant.Ghost) {
                    Text(text = stringResource(resource = Res.string.locators_cancel))
                }
                Button(
                    onClick = {
                        if (valid && beat != null) {
                            TimelineLocatorRepository.controller.edit(id = locator.id, name = name, beat = beat)
                            onDismiss()
                        }
                    },
                    enabled = valid,
                ) {
                    Text(text = stringResource(resource = Res.string.locators_save))
                }
            }
        }
    }
}
