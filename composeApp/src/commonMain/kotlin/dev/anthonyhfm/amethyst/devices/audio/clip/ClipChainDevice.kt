package dev.anthonyhfm.amethyst.devices.audio.clip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.echo.AudioDecoder
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.TextDial
import dev.anthonyhfm.amethyst.ui.components.WaveformView
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
        val deviceState by state.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Clip",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(
                    width = if (deviceState.isLoaded) {
                        500.dp
                    } else {
                        200.dp
                    }
                )
        ) {
            if (deviceState.isLoaded) {
                AudioView()
            } else {
                EmptyDeviceView()
            }
        }
    }

    @Composable
    private fun AudioView() {
        val deviceState by state.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                WaveformView(
                    signal = Signal.AudioSignal(
                        origin = this,
                        rawData = deviceState.rawData,
                        sampleRate = deviceState.sampleRate,
                        channels = deviceState.channels,
                        bitDepth = deviceState.bitDepth
                    ),
                    fadeInMs = deviceState.fadeInMs,
                    fadeOutMs = deviceState.fadeOutMs,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .height(80.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var beforeFadeIn = deviceState.fadeInMs
                TextDial(
                    headline = "Fade In",
                    text = "${deviceState.fadeInMs.toInt()} ms",
                    value = deviceState.fadeInMs / 1000f,
                    onStartValueChange = { v ->
                        beforeFadeIn = v * 1000f
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(fadeInMs = (value * 1000f).coerceIn(0f, 1000f))
                        }
                    },
                    onFinishValueChange = { v ->
                        pushStateChange(
                            before = state.value.copy(fadeInMs = beforeFadeIn),
                            after = state.value.copy(fadeInMs = (v * 1000f).coerceIn(0f, 1000f))
                        )
                    },
                    onResolveTextValue = { text ->
                        val msText = text.removeSuffix("ms").trim().toIntOrNull()
                        msText?.let { ms ->
                            if (ms in 0..1000) {
                                val before = state.value
                                state.update {
                                    it.copy(fadeInMs = ms.toFloat())
                                }
                                pushStateChange(before, state.value)
                            }
                        }
                    }
                )

                var beforeFadeOut = deviceState.fadeOutMs
                TextDial(
                    headline = "Fade Out",
                    text = "${deviceState.fadeOutMs.toInt()} ms",
                    value = deviceState.fadeOutMs / 1000f,
                    onStartValueChange = { v ->
                        beforeFadeOut = v * 1000f
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(fadeOutMs = (value * 1000f).coerceIn(0f, 1000f))
                        }
                    },
                    onFinishValueChange = { v ->
                        pushStateChange(
                            before = state.value.copy(fadeOutMs = beforeFadeOut),
                            after = state.value.copy(fadeOutMs = (v * 1000f).coerceIn(0f, 1000f))
                        )
                    },
                    onResolveTextValue = { text ->
                        val msText = text.removeSuffix("ms").trim().toIntOrNull()
                        msText?.let { ms ->
                            if (ms in 0..1000) {
                                val before = state.value
                                state.update {
                                    it.copy(fadeOutMs = ms.toFloat())
                                }
                                pushStateChange(before, state.value)
                            }
                        }
                    }
                )

                var beforeVolume = deviceState.volumeDb
                TextDial(
                    headline = "Volume",
                    text = "${if (deviceState.volumeDb >= 0) "+" else ""}${String.format("%.1f", deviceState.volumeDb)} dB",
                    value = (deviceState.volumeDb + 24f) / 48f,
                    onStartValueChange = { v ->
                        beforeVolume = (v * 48f) - 24f
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(volumeDb = ((value * 48f) - 24f).coerceIn(-24f, 24f))
                        }
                    },
                    onFinishValueChange = { v ->
                        pushStateChange(
                            before = state.value.copy(volumeDb = beforeVolume),
                            after = state.value.copy(volumeDb = ((v * 48f) - 24f).coerceIn(-24f, 24f))
                        )
                    },
                    onResolveTextValue = { text ->
                        val dbText = text.replace("dB", "").replace("+", "").trim().toFloatOrNull()
                        dbText?.let { db ->
                            if (db in -24f..24f) {
                                val before = state.value
                                state.update {
                                    it.copy(volumeDb = db)
                                }
                                pushStateChange(before, state.value)
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun EmptyDeviceView() {
        val scope = rememberCoroutineScope()
        val deviceState by state.collectAsState()

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
    }

    override fun signalEnter(n: List<Signal>) {
        n.filterIsInstance<Signal.Midi>().forEach { midiSignal ->
            if (midiSignal.velocity != 0 && state.value.isLoaded) {
                val deviceState = state.value

                // Apply volume, fade in, and fade out to raw audio data
                val processedData = applyAudioEffects(
                    rawData = deviceState.rawData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth,
                    fadeInMs = deviceState.fadeInMs,
                    fadeOutMs = deviceState.fadeOutMs,
                    volumeDb = deviceState.volumeDb
                )

                // Create AudioSignal with processed data
                val audioSignal = Signal.AudioSignal(
                    origin = this,
                    rawData = processedData,
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

    private fun applyAudioEffects(
        rawData: ByteArray?,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
        fadeInMs: Float,
        fadeOutMs: Float,
        volumeDb: Float
    ): ByteArray? {
        if (rawData == null || rawData.isEmpty()) return rawData

        val data = rawData.copyOf()
        val bytesPerSample = bitDepth / 8
        val frameSize = bytesPerSample * channels
        val totalFrames = data.size / frameSize
        
        // Calculate fade in/out in frames
        val fadeInFrames = ((fadeInMs / 1000f) * sampleRate).toInt().coerceAtMost(totalFrames)
        val fadeOutFrames = ((fadeOutMs / 1000f) * sampleRate).toInt().coerceAtMost(totalFrames)
        
        // Calculate volume gain (dB to linear)
        val volumeGain = kotlin.math.pow(10.0, (volumeDb / 20.0)).toFloat()

        for (frame in 0 until totalFrames) {
            var gain = volumeGain
            
            // Apply fade in
            if (frame < fadeInFrames) {
                val fadeInGain = frame.toFloat() / fadeInFrames.toFloat()
                gain *= fadeInGain
            }
            
            // Apply fade out
            if (frame >= totalFrames - fadeOutFrames) {
                val fadeOutGain = (totalFrames - frame).toFloat() / fadeOutFrames.toFloat()
                gain *= fadeOutGain
            }
            
            // Apply gain to all channels in this frame
            for (ch in 0 until channels) {
                val offset = frame * frameSize + ch * bytesPerSample
                
                when (bitDepth) {
                    8 -> {
                        val sample = data[offset].toInt() and 0xFF
                        val centered = sample - 128
                        val amplified = (centered * gain).toInt().coerceIn(-128, 127)
                        data[offset] = (amplified + 128).toByte()
                    }
                    16 -> {
                        val lo = data[offset].toInt() and 0xFF
                        val hi = data[offset + 1].toInt() shl 8
                        val sample = (lo or hi).toShort().toInt()
                        val amplified = (sample * gain).toInt().coerceIn(-32768, 32767)
                        data[offset] = (amplified and 0xFF).toByte()
                        data[offset + 1] = ((amplified shr 8) and 0xFF).toByte()
                    }
                    24 -> {
                        val b0 = data[offset].toInt() and 0xFF
                        val b1 = data[offset + 1].toInt() and 0xFF
                        val b2 = data[offset + 2].toInt()
                        var sample = b0 or (b1 shl 8) or (b2 shl 16)
                        if ((sample and 0x800000) != 0) sample = sample or -0x1000000
                        val amplified = (sample * gain).toInt().coerceIn(-8388608, 8388607)
                        data[offset] = (amplified and 0xFF).toByte()
                        data[offset + 1] = ((amplified shr 8) and 0xFF).toByte()
                        data[offset + 2] = ((amplified shr 16) and 0xFF).toByte()
                    }
                    32 -> {
                        val b0 = data[offset].toInt() and 0xFF
                        val b1 = data[offset + 1].toInt() and 0xFF
                        val b2 = data[offset + 2].toInt() and 0xFF
                        val b3 = data[offset + 3].toInt()
                        val sample = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                        val amplified = (sample * gain).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        data[offset] = (amplified and 0xFF).toByte()
                        data[offset + 1] = ((amplified shr 8) and 0xFF).toByte()
                        data[offset + 2] = ((amplified shr 16) and 0xFF).toByte()
                        data[offset + 3] = ((amplified shr 24) and 0xFF).toByte()
                    }
                }
            }
        }
        
        return data
    }
}

@Serializable
data class ClipChainDeviceState(
    val fileName: String = "",
    val rawData: ByteArray? = null,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val bitDepth: Int = 16,
    val isLoaded: Boolean = false,
    val fadeInMs: Float = 0f,
    val fadeOutMs: Float = 0f,
    val volumeDb: Float = 0f
) : DeviceState()
