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
import kotlin.math.pow

class ClipChainDevice : AudioChainDevice<ClipChainDeviceState>() {
    override val state = MutableStateFlow(ClipChainDeviceState())
    
    companion object {
        private const val VOLUME_MIN_DB = -24f
        private const val VOLUME_MAX_DB = 24f
        private const val VOLUME_RANGE_DB = VOLUME_MAX_DB - VOLUME_MIN_DB
        private const val MAX_FADE_MS = 1000f
        private const val SIGN_EXTEND_24BIT = -0x1000000
    }

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
                        450.dp
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
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
                    startPosition = deviceState.startPosition,
                    endPosition = deviceState.endPosition,
                    onStartPositionChange = { newStart ->
                        state.update { it.copy(startPosition = newStart.coerceIn(0f, it.endPosition - 0.01f)) }
                    },
                    onEndPositionChange = { newEnd ->
                        state.update { it.copy(endPosition = newEnd.coerceIn(it.startPosition + 0.01f, 1f)) }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var beforeFadeIn = deviceState.fadeInMs
                TextDial(
                    headline = "Fade In",
                    text = "${deviceState.fadeInMs.toInt()} ms",
                    value = deviceState.fadeInMs / MAX_FADE_MS,
                    onStartValueChange = { v ->
                        beforeFadeIn = deviceState.fadeInMs
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(fadeInMs = (value * MAX_FADE_MS).coerceIn(0f, MAX_FADE_MS))
                        }
                    },
                    onFinishValueChange = { v ->
                        pushStateChange(
                            before = state.value.copy(fadeInMs = beforeFadeIn),
                            after = state.value
                        )
                    },
                    onResolveTextValue = { text ->
                        val msText = text.removeSuffix("ms").trim().toIntOrNull()
                        msText?.let { ms ->
                            if (ms in 0..MAX_FADE_MS.toInt()) {
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
                    value = deviceState.fadeOutMs / MAX_FADE_MS,
                    onStartValueChange = { v ->
                        beforeFadeOut = deviceState.fadeOutMs
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(fadeOutMs = (value * MAX_FADE_MS).coerceIn(0f, MAX_FADE_MS))
                        }
                    },
                    onFinishValueChange = { v ->
                        pushStateChange(
                            before = state.value.copy(fadeOutMs = beforeFadeOut),
                            after = state.value
                        )
                    },
                    onResolveTextValue = { text ->
                        val msText = text.removeSuffix("ms").trim().toIntOrNull()
                        msText?.let { ms ->
                            if (ms in 0..MAX_FADE_MS.toInt()) {
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
                    text = "${if (deviceState.volumeDb >= 0) "+" else ""}${deviceState.volumeDb} dB",
                    value = (deviceState.volumeDb - VOLUME_MIN_DB) / VOLUME_RANGE_DB,
                    onStartValueChange = { v ->
                        beforeVolume = deviceState.volumeDb
                    },
                    onValueChange = { value ->
                        state.update {
                            it.copy(volumeDb = ((value * VOLUME_RANGE_DB) + VOLUME_MIN_DB).coerceIn(VOLUME_MIN_DB, VOLUME_MAX_DB))
                        }
                    },
                    onFinishValueChange = { v ->
                        pushStateChange(
                            before = state.value.copy(volumeDb = beforeVolume),
                            after = state.value
                        )
                    },
                    onResolveTextValue = { text ->
                        val dbText = text.replace("dB", "").replace("+", "").trim().toFloatOrNull()
                        dbText?.let { db ->
                            if (db in VOLUME_MIN_DB..VOLUME_MAX_DB) {
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
                // Vorherigen Clip abbrechen, falls einer läuft
                dev.anthonyhfm.amethyst.core.engine.echo.Echo.cancel(this)
                val deviceState = state.value

                // Apply volume, fade in, fade out, and trim to start/end positions
                val processedData = applyAudioEffects(
                    rawData = deviceState.rawData,
                    sampleRate = deviceState.sampleRate,
                    channels = deviceState.channels,
                    bitDepth = deviceState.bitDepth,
                    fadeInMs = deviceState.fadeInMs,
                    fadeOutMs = deviceState.fadeOutMs,
                    volumeDb = deviceState.volumeDb,
                    startPosition = deviceState.startPosition,
                    endPosition = deviceState.endPosition
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
        volumeDb: Float,
        startPosition: Float,
        endPosition: Float
    ): ByteArray? {
        if (rawData == null || rawData.isEmpty()) return rawData

        val bytesPerSample = bitDepth / 8
        val frameSize = bytesPerSample * channels
        val totalFrames = rawData.size / frameSize
        
        // Calculate start and end frames based on positions
        val startFrame = (totalFrames * startPosition).toInt().coerceIn(0, totalFrames)
        val endFrame = (totalFrames * endPosition).toInt().coerceIn(startFrame, totalFrames)
        val activeFrames = endFrame - startFrame
        
        if (activeFrames <= 0) return ByteArray(0)
        
        // Create output array with only the active region
        val outputData = ByteArray(activeFrames * frameSize)
        
        // Calculate fade in/out in frames (relative to active region)
        val fadeInFrames = ((fadeInMs / 1000f) * sampleRate).toInt().coerceAtMost(activeFrames)
        val fadeOutFrames = ((fadeOutMs / 1000f) * sampleRate).toInt().coerceAtMost(activeFrames)
        val fadeOutStartFrame = activeFrames - fadeOutFrames
        
        val volumeGain = 10.0.pow(volumeDb / 20.0).toFloat()

        for (frame in 0 until activeFrames) {
            var gain = volumeGain
            
            // Apply fade in
            if (frame < fadeInFrames && fadeInFrames > 0) {
                val fadeInGain = frame.toFloat() / fadeInFrames.toFloat()
                gain *= fadeInGain
            }
            
            // Apply fade out
            if (frame >= fadeOutStartFrame && fadeOutFrames > 0) {
                val fadeOutGain = (activeFrames - frame).toFloat() / fadeOutFrames.toFloat()
                gain *= fadeOutGain
            }
            
            // Apply gain to all channels in this frame
            for (ch in 0 until channels) {
                val sourceOffset = (startFrame + frame) * frameSize + ch * bytesPerSample
                val destOffset = frame * frameSize + ch * bytesPerSample
                
                when (bitDepth) {
                    8 -> {
                        val sample = rawData[sourceOffset].toInt() and 0xFF
                        val centered = sample - 128
                        val amplified = (centered * gain).toInt().coerceIn(-128, 127)
                        outputData[destOffset] = (amplified + 128).toByte()
                    }
                    16 -> {
                        val lo = rawData[sourceOffset].toInt() and 0xFF
                        val hi = rawData[sourceOffset + 1].toInt() shl 8
                        val sample = (hi or lo).toShort().toInt()
                        val amplified = (sample * gain).toInt().coerceIn(-32768, 32767)
                        outputData[destOffset] = (amplified and 0xFF).toByte()
                        outputData[destOffset + 1] = ((amplified shr 8) and 0xFF).toByte()
                    }
                    24 -> {
                        val b0 = rawData[sourceOffset].toInt() and 0xFF
                        val b1 = rawData[sourceOffset + 1].toInt() and 0xFF
                        val b2 = rawData[sourceOffset + 2].toInt() and 0xFF
                        var sample = b0 or (b1 shl 8) or (b2 shl 16)
                        if ((sample and 0x800000) != 0) sample = sample or SIGN_EXTEND_24BIT
                        val amplified = (sample * gain).toInt().coerceIn(-8388608, 8388607)
                        outputData[destOffset] = (amplified and 0xFF).toByte()
                        outputData[destOffset + 1] = ((amplified shr 8) and 0xFF).toByte()
                        outputData[destOffset + 2] = ((amplified shr 16) and 0xFF).toByte()
                    }
                    32 -> {
                        val b0 = rawData[sourceOffset].toInt() and 0xFF
                        val b1 = rawData[sourceOffset + 1].toInt() and 0xFF
                        val b2 = rawData[sourceOffset + 2].toInt() and 0xFF
                        val b3 = rawData[sourceOffset + 3].toInt() and 0xFF
                        val sample = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                        val amplified = (sample * gain).toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        outputData[destOffset] = (amplified and 0xFF).toByte()
                        outputData[destOffset + 1] = ((amplified shr 8) and 0xFF).toByte()
                        outputData[destOffset + 2] = ((amplified shr 16) and 0xFF).toByte()
                        outputData[destOffset + 3] = ((amplified shr 24) and 0xFF).toByte()
                    }
                }
            }
        }
        
        return outputData
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
    val volumeDb: Float = 0f,
    val startPosition: Float = 0f, // 0.0 to 1.0
    val endPosition: Float = 1f    // 0.0 to 1.0
) : DeviceState()
