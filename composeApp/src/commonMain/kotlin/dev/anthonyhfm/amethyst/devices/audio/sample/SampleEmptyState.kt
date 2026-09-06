package dev.anthonyhfm.amethyst.devices.audio.sample

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.twotone.AudioFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SampleEmptyState(
    state: MutableStateFlow<SampleChainDeviceState>,
    onLoaded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    Empty(
        modifier = modifier.fillMaxSize()
    ) {
        EmptyIcon(imageVector = Icons.TwoTone.AudioFile)
        EmptyTitle(text = "No sample loaded")

        Spacer(Modifier.weight(1f))

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
                                AudioLibraryRepository.importFile(selectedFile)?.let { source ->
                                    state.update { currentState ->
                                        currentState.copy(
                                            fileName = source.fileName,
                                            rawData = null,
                                            sampleRate = source.sampleRate,
                                            channels = source.channels,
                                            bitDepth = source.bitDepth,
                                            totalDurationMs = source.totalDurationMs,
                                            isLoaded = true,
                                            sourceId = source.id,
                                            sourceStartFrame = 0L,
                                            sourceEndFrameExclusive = source.totalSamples,
                                        )
                                    }
                                    // Snapshot creation can include high-quality sample-rate
                                    // conversion. Keep that work off the UI thread.
                                    withContext(Dispatchers.Default) {
                                        onLoaded()
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
