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
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class ClipChainDevice : AudioChainDevice<ClipChainDeviceState>() {
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
                                extensions = AudioDecoder.getSupportedFormats()
                            )
                        )

                        file?.let { selectedFile ->
                            try {
                                // Decode audio file using new AudioDecoder
                                val audioSignal = AudioDecoder.decodeAudioData(
                                    audioData = selectedFile.readBytes(),
                                    fileName = selectedFile.name
                                )

                                audioSignal?.let { signal ->
                                    state.update { currentState ->
                                        currentState.copy(
                                            fileName = selectedFile.name,
                                            rawData = signal.rawData,
                                            sampleRate = signal.sampleRate,
                                            channels = signal.channels,
                                            bitDepth = signal.bitDepth,
                                            isLoaded = true
                                        )
                                    }
                                    println("Audio loaded successfully: ${selectedFile.name}")
                                } ?: run {
                                    println("Failed to decode audio file: ${selectedFile.name}")
                                    state.update { currentState ->
                                        currentState.copy(
                                            fileName = "Failed to load",
                                            isLoaded = false
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                println("Error loading audio file: ${e.message}")
                                state.update { currentState ->
                                    currentState.copy(
                                        fileName = "Error loading file",
                                        isLoaded = false
                                    )
                                }
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Default.FileOpen, null)
            }

            if (!deviceState.isLoaded) {
                Text(
                    text = if (deviceState.fileName.isEmpty()) "Please select an audio file" else deviceState.fileName,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 6.dp)
                )
            } else {
                Text(
                    text = deviceState.fileName,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(vertical = 6.dp)
                )
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        n.filterIsInstance<Signal.Midi>().forEach { midiSignal ->
            if (midiSignal.velocity != 0 && state.value.isLoaded) {
                val deviceState = state.value

                // Create AudioSignal directly from stored PCM data
                val audioSignal = Signal.AudioSignal(
                    origin = this,
                    rawData = deviceState.rawData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth
                )

                signalExit?.invoke(listOf(audioSignal))
            } else if (midiSignal.velocity != 0 && !state.value.isLoaded) {
                println("Clip device triggered but no audio loaded")
                // Pass through the original signal if no audio is loaded
                signalExit?.invoke(n)
            }
        }
    }
}

@Serializable
data class ClipChainDeviceState(
    val fileName: String = "",
    val rawData: ByteArray? = null,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16,
    val isLoaded: Boolean = false
) : DeviceState() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClipChainDeviceState) return false
        if (fileName != other.fileName) return false
        if (rawData != null) {
            if (other.rawData == null) return false
            if (!rawData.contentEquals(other.rawData)) return false
        } else if (other.rawData != null) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (bitDepth != other.bitDepth) return false
        if (isLoaded != other.isLoaded) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + (rawData?.contentHashCode() ?: 0)
        result = 31 * result + sampleRate
        result = 31 * result + channels
        result = 31 * result + bitDepth
        result = 31 * result + isLoaded.hashCode()
        return result
    }
}
