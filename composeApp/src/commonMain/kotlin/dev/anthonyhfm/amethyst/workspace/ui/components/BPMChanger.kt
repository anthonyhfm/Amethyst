package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.metronome
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

@Composable
fun BPMChanger() {
    var bpmText: String by remember { mutableStateOf(WorkspaceRepository.bpm.value.toString()) }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .height(44.dp)
            .width(112.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.2f), CircleShape)
            .padding(horizontal = 12.dp),

        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.metronome),
            contentDescription = "Beats per minute",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(24.dp)
        )

        BasicTextField(
            value = bpmText,
            onValueChange = { newValue ->
                if (newValue.length <= 6) {
                    if (newValue.isEmpty()) {
                        bpmText = "0"
                        WorkspaceRepository.setBpm(0.0)
                    } else if (isValidBpmInput(newValue)) {
                        val normalizedValue = newValue.replace(',', '.')
                        
                        try {
                            val numericValue = normalizedValue.toDoubleOrNull() ?: 0.0
                            
                            if (numericValue <= 999.99) {
                                bpmText = newValue
                                WorkspaceRepository.setBpm(numericValue)
                            }
                        } catch (_: Exception) {}
                    }
                }
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.titleMedium.fontSize,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
        )
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

