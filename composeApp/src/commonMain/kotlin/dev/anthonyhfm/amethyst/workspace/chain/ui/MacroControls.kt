package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.DefaultShape
import dev.anthonyhfm.amethyst.ui.components.primitives.StepTextDial
import dev.anthonyhfm.amethyst.ui.theme.chainBorder
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainSurface
import dev.anthonyhfm.amethyst.ui.theme.cardForeground
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.secondary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro

@Composable
fun MacroControls() {
    var macrosVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(DefaultShape)
            .background(Theme[chainColorTokens][chainSurface], DefaultShape)
            .border(1.dp, Theme[chainColorTokens][chainBorder], DefaultShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (macrosVisible) {
            Row(
                modifier = Modifier
                    .height(116.dp),

                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { macrosVisible = !macrosVisible },
                        variant = ButtonVariant.Ghost,
                        size = ButtonSize.Icon,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Toggle Macros",
                            modifier = Modifier.rotate(-90f),
                        )
                    }
                }

                MacroList()
            }
        } else {
            Button(
                onClick = { macrosVisible = !macrosVisible },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Small,
                modifier = Modifier.padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Toggle Macros",
                    modifier = Modifier.rotate(0f),
                )

                Text(
                    text = "Global Macros",
                    style = Theme[typography][small],
                    color = Theme[colors][cardForeground],
                )
            }
        }
    }
}

@Composable
fun MacroList() {
    val macros by WorkspaceRepository.macros.collectAsState()

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        macros.forEachIndexed { index, macro ->
            StepTextDial(
                headline = "Macro ${index + 1}",
                text = macro.value.toString(),
                steps = IntArray(100) { it }.toList(),
                value = macro.value,
                containerColor = Theme[colors][secondary],
                dialColor = Theme[colors][primary],
                onResolveTextValue = {
                    val valueText = it.trim().toIntOrNull()

                    valueText?.let { value ->
                        if (value in 0..127) {
                            WorkspaceRepository.setMacroValue(
                                index = index,
                                macro = macro.copy(
                                    value = value
                                )
                            )
                        }
                    }
                },
                onValueChange = {
                    WorkspaceRepository.setMacroValue(
                        index = index,
                        macro = macro.copy(
                            value = it
                        )
                    )
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(116.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { WorkspaceRepository.addMacro(Macro(value = 0)) },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Icon,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Macro",
            )
        }
    }
}
