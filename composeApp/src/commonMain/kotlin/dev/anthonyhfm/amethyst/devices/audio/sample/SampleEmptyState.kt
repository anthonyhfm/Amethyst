package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Empty
import dev.anthonyhfm.amethyst.ui.components.primitives.EmptyActions
import dev.anthonyhfm.amethyst.ui.components.primitives.EmptyDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.EmptyIcon
import dev.anthonyhfm.amethyst.ui.components.primitives.EmptyTitle
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.secondaryForeground
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun SampleEmptyState(
    state: MutableStateFlow<SampleChainDeviceState>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Empty(
        modifier = modifier.fillMaxSize()
    ) {
        EmptyIcon(imageVector = Icons.TwoTone.AudioFile)
        EmptyTitle(text = "No sample loaded")
        EmptyDescription(text = "Choose an audio file to view and edit waveform")
        EmptyActions {
            Button(
                onClick = {
                    scope.launch {
                        val file = FileKit.openFilePicker(
                            mode = FileKitMode.Single,
                            title = "Select Audio File",
                            type = FileKitType.File(
                                extensions = Echo.getSupportedFormats()
                            )
                        )

                        file?.let { selectedFile ->
                            try {
                                val audioSignal = Echo.decodeAudioData(
                                    audioData = selectedFile.readBytes(),
                                    fileName = selectedFile.name
                                )

                                audioSignal?.let { signal ->
                                    val bytesPerSample = signal.bitDepth / 8
                                    val frameSize = bytesPerSample * signal.channels
                                    val totalFrames = (signal.rawData?.size ?: 0) / frameSize
                                    val durationMs = ((totalFrames.toFloat() / signal.sampleRate) * 1000f).toLong()

                                    state.update { currentState ->
                                        currentState.copy(
                                            fileName = selectedFile.name,
                                            rawData = signal.rawData,
                                            sampleRate = signal.sampleRate,
                                            channels = signal.channels,
                                            bitDepth = signal.bitDepth,
                                            totalDurationMs = durationMs,
                                            isLoaded = true
                                        )
                                    }
                                } ?: run {
                                    println("Failed to decode audio file: ${selectedFile.name}")
                                }
                            } catch (e: Exception) {
                                println("Error loading audio file: ${e.message}")
                            }
                        }
                    }
                },
                variant = ButtonVariant.Secondary
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    tint = Theme[colors][secondaryForeground]
                )
                Text("Open Sample")
            }
        }
    }
}
