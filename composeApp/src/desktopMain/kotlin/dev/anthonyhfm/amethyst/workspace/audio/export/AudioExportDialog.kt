package dev.anthonyhfm.amethyst.workspace.audio.export

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Dialog
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogContent
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.DialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Progress
import dev.anthonyhfm.amethyst.ui.components.primitives.Select
import dev.anthonyhfm.amethyst.ui.components.primitives.SelectItem
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import javax.swing.JOptionPane
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun AudioExportHost() {
    val request by AudioExport.request.collectAsState()
    request?.let { value ->
        key(value) {
            AudioExportDialog(request = value)
        }
    }
}

@Composable
private fun AudioExportDialog(request: AudioExportRequest) {
    val state = rememberDialogState(initiallyVisible = true)
    val scope = rememberCoroutineScope()
    var useSelection by remember(key1 = request) { mutableStateOf(value = request.selection != null) }
    var options by remember(key1 = request) {
        mutableStateOf(value = AudioExportOptions(sampleRate = request.source?.originalSampleRate ?: 48_000))
    }
    var job by remember { mutableStateOf<Job?>(value = null) }
    var progress by remember { mutableStateOf(value = 0f) }
    var error by remember { mutableStateOf<String?>(value = null) }
    var errorTitle by remember { mutableStateOf(value = Res.string.audio_export_failed) }
    val snapshot = request.source ?: if (useSelection) request.selection else request.entire
    val sampleRates = listOfNotNull(
        snapshot?.originalSampleRate?.takeIf { options.format == AudioExportFormat.WAV },
        44_100,
        48_000,
    ).distinct()

    DisposableEffect(key1 = request) {
        onDispose {
            val active = job
            if (active == null) {
                AudioExport.close(expectedRequest = request)
            } else {
                active.invokeOnCompletion { AudioExport.close(expectedRequest = request) }
                active.cancel()
            }
        }
    }
    val busy = job != null
    val exportLabel = stringResource(resource = Res.string.audio_export_title)
    val overwriteLabel = stringResource(resource = Res.string.audio_export_overwrite)
    val selectionLabel = stringResource(resource = Res.string.audio_export_selection)
    val entireLabel = stringResource(resource = Res.string.audio_export_entire)

    fun dismiss() {
        if (busy) {
            job?.cancel()
        } else {
            AudioExport.close(expectedRequest = request)
        }
    }

    Dialog(state = state, onDismiss = ::dismiss) {
        DialogContent(
            modifier = Modifier
                .width(width = 480.dp)
                .heightIn(max = 720.dp),
            showCloseButton = !busy,
        ) {
            DialogHeader {
                DialogTitle(text = stringResource(resource = Res.string.audio_export_heading))

                snapshot?.let { audio ->
                    DialogDescription(
                        text = request.source?.clips?.singleOrNull()?.source?.fileName ?: audio.name,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(weight = 1f, fill = false)
                    .verticalScroll(state = rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                if (request.source == null) {
                    Text(text = stringResource(resource = Res.string.audio_export_scope))

                    Select(
                        value = if (useSelection) selectionLabel else entireLabel,
                        onValueChange = {},
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        SelectItem(
                            text = selectionLabel,
                            selected = useSelection,
                            enabled = request.selection != null,
                            onClick = { useSelection = true },
                        )

                        SelectItem(
                            text = entireLabel,
                            selected = !useSelection,
                            enabled = request.entire != null,
                            onClick = { useSelection = false },
                        )
                    }
                }

                snapshot?.let {
                    DialogDescription(
                        text = stringResource(
                            resource = Res.string.audio_export_range,
                            exportTime(timeUs = it.startUs),
                            exportTime(timeUs = it.endUs),
                            exportTime(timeUs = it.durationUs),
                        ),
                    )
                }

                Text(text = stringResource(resource = Res.string.audio_export_format))

                Select(
                    value = options.format.name,
                    onValueChange = {},
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    AudioExportFormat.entries.forEach { format ->
                        SelectItem(
                            text = format.name,
                            selected = options.format == format,
                            onClick = {
                                options = options.copy(
                                    format = format,
                                    sampleRate = if (format == AudioExportFormat.MP3 && options.sampleRate !in setOf(44_100, 48_000)) {
                                        48_000
                                    } else {
                                        options.sampleRate
                                    },
                                )
                            },
                        )
                    }
                }

                ExportNumberSelect(
                    label = stringResource(resource = Res.string.audio_export_sample_rate),
                    value = options.sampleRate,
                    values = sampleRates,
                    enabled = !busy,
                    display = { "$it Hz" },
                    onChange = { options = options.copy(sampleRate = it) },
                )

                if (options.format == AudioExportFormat.WAV) {
                    ExportNumberSelect(
                        label = stringResource(resource = Res.string.audio_export_bit_depth),
                        value = options.bitDepth,
                        values = listOf(16, 24),
                        enabled = !busy,
                        display = { "$it Bit" },
                        onChange = { options = options.copy(bitDepth = it) },
                    )
                } else {
                    ExportNumberSelect(
                        label = stringResource(resource = Res.string.audio_export_quality),
                        value = options.bitrate,
                        values = listOf(128, 192, 320),
                        enabled = !busy,
                        display = { "$it kbit/s" },
                        onChange = { options = options.copy(bitrate = it) },
                    )
                }

                when {
                    snapshot == null -> DialogDescription(text = stringResource(resource = Res.string.audio_export_empty))
                    !snapshot.hasAudibleClips -> DialogDescription(text = stringResource(resource = Res.string.audio_export_muted))
                    snapshot.hasMissingSources -> DialogDescription(text = stringResource(resource = Res.string.audio_export_missing))
                }

                if (busy) {
                    Text(text = stringResource(resource = Res.string.audio_export_progress, (progress * 100f).toInt()))

                    Progress(value = progress)
                }

                error?.let { message ->
                    Text(text = stringResource(resource = errorTitle))

                    DialogDescription(text = message)
                }
            }

            DialogFooter {
                Button(onClick = ::dismiss, variant = ButtonVariant.Outline) {
                    Text(text = stringResource(resource = Res.string.common_cancel))
                }

                Button(
                    enabled = !busy && snapshot != null && snapshot.hasAudibleClips && !snapshot.hasMissingSources,
                    onClick = {
                        val input = snapshot ?: return@Button
                        error = null
                        errorTitle = Res.string.audio_export_failed
                        progress = 0f
                        val task = scope.launch(start = CoroutineStart.LAZY) {
                            try {
                                val selected = FileKit.openFileSaver(
                                    suggestedName = input.name,
                                    defaultExtension = options.format.extension,
                                ) ?: return@launch
                                val raw = Paths.get(selected.path)
                                val target = audioExportDestination(path = raw, format = options.format)
                                if (target != raw && Files.exists(target)) {
                                    val result = JOptionPane.showConfirmDialog(null, overwriteLabel, exportLabel, JOptionPane.YES_NO_OPTION)
                                    if (result != JOptionPane.YES_OPTION) {
                                        return@launch
                                    }
                                }
                                withContext(context = Dispatchers.IO) {
                                    val context = currentCoroutineContext()
                                    var lastUpdate = 0L
                                    writeAudioExport(
                                        snapshot = input,
                                        options = options,
                                        destination = target,
                                    ) { value ->
                                        context.ensureActive()
                                        val now = System.nanoTime()
                                        if (now - lastUpdate > 50_000_000L || value == 1f) {
                                            progress = value
                                            lastUpdate = now
                                        }
                                    }
                                }
                                errorTitle = Res.string.audio_export_reveal_failed
                                withContext(context = Dispatchers.IO) {
                                    revealExportedAudio(path = target)
                                }
                                job = null
                                AudioExport.close(expectedRequest = request)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: failure.javaClass.simpleName
                            } finally {
                                job = null
                            }
                        }
                        job = task
                        task.start()
                    },
                ) {
                    Text(text = exportLabel)
                }
            }
        }
    }
}

@Composable
private fun ExportNumberSelect(
    label: String,
    value: Int,
    values: List<Int>,
    enabled: Boolean,
    display: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    Text(text = label)

    Select(
        value = display(value),
        onValueChange = {},
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        values.forEach { option ->
            SelectItem(
                text = display(option),
                selected = option == value,
                onClick = { onChange(option) },
            )
        }
    }
}

internal fun audioExportDestination(path: Path, format: AudioExportFormat): Path {
    return if (path.fileName.toString().endsWith(suffix = ".${format.extension}", ignoreCase = true)) {
        path
    } else {
        path.resolveSibling("${path.fileName}.${format.extension}")
    }
}

private fun exportTime(timeUs: Long): String {
    val millis = timeUs / 1000L
    return String.format(Locale.ROOT, "%d:%02d.%03d", millis / 60_000L, millis / 1000L % 60L, millis % 1000L)
}
