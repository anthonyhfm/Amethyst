package dev.anthonyhfm.amethyst.devices.audio.clip

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.audio.AudioPlayer
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class ClipChainDevice : ChainDevice<ClipChainDeviceState>() {
    override val state = MutableStateFlow(ClipChainDeviceState())

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Clip",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(200.dp)
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        val file = FileKit.openFilePicker(
                            mode = FileKitMode.Single,
                            title = "Select Audio File",
                            type = FileKitType.File(
                                extensions = listOf("wav", "ogg", "mp3")
                            )
                        )

                        file?.let {
                            val uuid = AudioPlayer.loadAudio(file.readBytes())

                            state.update {
                                it.copy(
                                    audioKey = uuid
                                )
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Default.FileOpen, null)
            }

            if (deviceState.audioKey.isEmpty()) {
                Text(
                    text = "Please select an audio file",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 6.dp)
                )
            } else {
                Text(
                    text = WorkspaceRepository.audioRegistry[deviceState.audioKey]?.name ?: "Unnamed Audio",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 6.dp)
                )
            }
        }
    }

    override fun midiEnter(n: List<Signal>) {
        n.forEach {
            if (it.color != Color.Black) {
                if (WorkspaceRepository.audioRegistry[state.value.audioKey] != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        AudioPlayer.stopAudio(state.value.audioKey)
                        AudioPlayer.playAudio(state.value.audioKey)
                    }
                }
            }
        }

        midiExit?.invoke(n)
    }
}

@Serializable
data class ClipChainDeviceState(
    val audioKey: String = "",
) : DeviceState()