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
import dev.anthonyhfm.amethyst.devices.ChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.pickFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ClipChainDevice : ChainDevice<ClipChainDeviceState>() {
    override val state = MutableStateFlow(ClipChainDeviceState())

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val deviceState by state.collectAsState()

        AmethystDevice(
            title = "Clip",
            deviceId = internalUUID,
            modifier = Modifier
                .width(200.dp)
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        val file = FileKit.pickFile(
                            mode = PickerMode.Single,
                            title = "Select Audio File",
                            type = PickerType.File(
                                extensions = listOf("wav", "ogg")
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
                    AudioPlayer.playAudio(state.value.audioKey)
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