package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.ui.components.Dial
import dev.anthonyhfm.amethyst.ui.components.StepTextDial
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.Macro

@Composable
fun MacroControls() {
    var macrosVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surfaceColorAtElevation(24.dp)))
            .border(1.dp, MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(12.dp)),
    ) {
        if (macrosVisible) {
            Row(
                modifier = Modifier
                    .height(116.dp),

                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(CircleShape)
                        .size(48.dp)
                        .clickable {
                            macrosVisible = !macrosVisible
                        },

                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Toggle Macros",
                        modifier = Modifier
                            .rotate(-90f)
                    )
                }

                MacroList()
            }
        } else {
            Row(
                modifier = Modifier
                    .clickable {
                        macrosVisible = !macrosVisible
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp),

                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Toggle Macros",
                        modifier = Modifier
                            .rotate(0f)
                    )
                }

                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(start = 6.dp, end = 12.dp),

                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Macros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
                steps = IntArray(128) { it }.toList(),
                value = 0,
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
        IconButton(
            onClick = {
                WorkspaceRepository.addMacro(Macro(value = 0))
            }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Macro",
            )
        }
    }
}