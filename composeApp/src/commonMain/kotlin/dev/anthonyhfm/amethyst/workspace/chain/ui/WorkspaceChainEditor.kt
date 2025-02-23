package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract

@Composable
fun WorkspaceChainEditor(
    devices: List<EffectDevice<*>>,
    onEvent: (WorkspaceContract.Event) -> Unit
) {
    Row(
        modifier = Modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .height(280.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp), RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        devices.forEachIndexed { index, device ->
            HiddenDevicePickerButton(
                expanded = false,
                onAddComponent = {
                    onEvent(WorkspaceContract.Event.AddChainDevice(it, index))
                }
            )

            device.Content()
        }

        HiddenDevicePickerButton(
            expanded = true,
            onAddComponent = {
                onEvent(WorkspaceContract.Event.AddChainDevice(it))
            }
        )
    }
}