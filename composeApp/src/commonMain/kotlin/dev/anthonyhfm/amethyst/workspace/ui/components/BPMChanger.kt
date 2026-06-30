package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Timer
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import androidx.compose.ui.focus.onFocusChanged
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

@Composable
fun BPMChanger() {
    val bpm by WorkspaceRepository.bpm.collectAsState()
    var bpmText: String by remember { mutableStateOf(bpm.toString()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(bpm, isFocused) {
        if (!isFocused) {
            bpmText = bpm.toString()
        }
    }

    WorkspaceToolbarSurface(
        modifier = Modifier.width(120.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Lucide.Timer,
                contentDescription = "Beats per minute",
                tint = Theme[colors][mutedForeground],
                modifier = Modifier.size(16.dp),
            )

            BasicTextField(
                value = bpmText,
                onValueChange = { newValue ->
                    if (newValue.length > 6) return@BasicTextField

                    if (newValue.isEmpty()) {
                        bpmText = "0"
                        WorkspaceRepository.setBpm(0.0)
                        return@BasicTextField
                    }

                    if (!isValidBpmInput(newValue)) return@BasicTextField

                    val normalizedValue = newValue.replace(',', '.')
                    val numericValue = normalizedValue.toDoubleOrNull() ?: return@BasicTextField

                    if (numericValue <= 999.99) {
                        bpmText = newValue
                        WorkspaceRepository.setBpm(numericValue)
                    }
                },
                textStyle = Theme[typography][small].copy(
                    textAlign = TextAlign.Center,
                    color = Theme[colors][foreground],
                ),
                cursorBrush = SolidColor(Theme[colors][foreground]),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .width(56.dp)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        WorkspaceRepository.isInputFocused = it.isFocused
                        if (!it.isFocused) {
                            bpmText = WorkspaceRepository.bpm.value.toString()
                        }
                    },
            )
        }
    }
}

private fun isValidBpmInput(input: String): Boolean {
    if (input.isEmpty()) return true

    val validChars = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ',')
    if (input.any { it !in validChars }) return false

    val dotCount = input.count { it == '.' }
    val commaCount = input.count { it == ',' }

    return dotCount + commaCount <= 1
}
