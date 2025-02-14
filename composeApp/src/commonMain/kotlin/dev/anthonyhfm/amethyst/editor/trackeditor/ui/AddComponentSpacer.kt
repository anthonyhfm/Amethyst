package dev.anthonyhfm.amethyst.editor.trackeditor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import dev.anthonyhfm.amethyst.editor.plugins.EffectDevice

@Composable
fun AddComponentSpacer(
    expanded: Boolean = false,
    onAddComponent: (EffectDevice) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovering: Boolean by interaction.collectIsHoveredAsState()
    var pickerVisible: Boolean by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(
                width = animateDpAsState(
                    targetValue = if (hovering || expanded || pickerVisible) {
                        56.dp
                    } else {
                        12.dp
                    }
                ).value
            )
            .hoverable(interaction),

        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = hovering || expanded || pickerVisible,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box {
                IconButton(
                    onClick = {
                        pickerVisible = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add a new device"
                    )
                }

                ComponentPicker(
                    visible = pickerVisible,
                    onDismiss = {
                        pickerVisible = false
                    },
                    onPickComponent = {
                        onAddComponent(it)
                    }
                )
            }
        }
    }
}