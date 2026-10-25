package dev.anthonyhfm.amethyst.workspace.ui.components

import amethyst.composeapp.generated.resources.*
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Upload
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.conversion.ableton.utils.MidiFileImporter
import dev.anthonyhfm.amethyst.core.util.fileDialogSettings
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesImageImporter
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.Spinner
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.selectLaunchpad
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

internal class KeyframesImporter(private val scope: CoroutineScope) {
    var busy by mutableStateOf(value = false)
        private set
    var error by mutableStateOf<String?>(value = null)

    fun import(
        mode: KeyframesWorkspaceMode,
        pickerTitle: String,
        targetTitle: String,
    ) {
        if (busy) {
            return
        }

        busy = true

        scope.launch {
            try {
                val file = FileKit.openFilePicker(
                    type = FileKitType.File(extensions = listOf("png", "gif", "mid")),
                    dialogSettings = fileDialogSettings(title = pickerTitle),
                ) ?: return@launch
                if (WorkspaceRepository.mode.value !== mode) {
                    return@launch
                }
                val launchpadId = if (file.extension.equals(other = "mid", ignoreCase = true)) {
                    WorkspaceRepository.selectLaunchpad(
                        title = targetTitle,
                    )?.launchpadId ?: return@launch
                } else {
                    null
                }
                importFile(mode = mode, file = file, launchpadId = launchpadId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = exception.message ?: "The file could not be imported. Try another PNG, GIF, or MID file."
            } finally {
                busy = false
            }
        }
    }

    private suspend fun importFile(mode: KeyframesWorkspaceMode, file: PlatformFile, launchpadId: String?) {
        val device = requireNotNull(mode.parentDevice)
        val previousFrames = device.state.value.frames
        val bounds = WorkspaceRepository.bounds
        val bpm = WorkspaceRepository.bpm.value
        val targets = ViewportRepository.devices.value.map {
            KeyframesImageImporter.Target(
                launchpadId = it.launchpadId,
                position = IntOffset(x = it.position.value.x.toInt(), y = it.position.value.y.toInt()),
                size = IntSize(width = it.layout.cols, height = it.layout.rows),
            )
        }

        val frames = withContext(context = Dispatchers.IO) {
            val bytes = file.readBytes()
            when (file.extension.lowercase()) {
                "png", "gif" -> KeyframesImageImporter.load(data = bytes, bounds = bounds, targets = targets)

                "mid" -> {
                    val target = requireNotNull(targets.firstOrNull { it.launchpadId == launchpadId }) {
                        "The selected Launchpad is no longer in the workspace."
                    }
                    MidiFileImporter.loadData(
                        data = bytes,
                        bpm = bpm,
                        launchpad = MidiFileImporter.DeviceTarget(
                            launchpadId = target.launchpadId,
                            offset = target.position,
                        ),
                    ).frames
                }

                else -> throw IllegalArgumentException("Choose a PNG, GIF, or MID file.")
            }
        }

        val currentTargets = ViewportRepository.devices.value.map {
            KeyframesImageImporter.Target(
                launchpadId = it.launchpadId,
                position = IntOffset(x = it.position.value.x.toInt(), y = it.position.value.y.toInt()),
                size = IntSize(width = it.layout.cols, height = it.layout.rows),
            )
        }

        require(
            WorkspaceRepository.mode.value === mode
                && device.state.value.frames === previousFrames
                && targets == currentTargets
                && bounds == WorkspaceRepository.bounds
        ) {
            "The workspace changed during import. Please import the file again."
        }

        device.replaceFrames(frames = frames)
    }
}

@Composable
internal fun rememberKeyframesImporter(): KeyframesImporter {
    val scope = rememberCoroutineScope()
    val importer = remember(scope) { KeyframesImporter(scope = scope) }
    val errorDialog = rememberDialogState()

    LaunchedEffect(key1 = importer.error) {
        errorDialog.visible = importer.error != null
    }

    AlertDialog(
        state = errorDialog,
        onDismiss = { importer.error = null },
    ) {
        AlertDialogHeader {
            AlertDialogTitle(text = stringResource(Res.string.keyframes_import_error_title))

            AlertDialogDescription(text = importer.error.orEmpty())
        }

        AlertDialogFooter {
            AlertDialogCancel(onClick = { importer.error = null }) {
                Text(text = stringResource(Res.string.keyframes_import_close))
            }
        }
    }
    return importer
}

@Composable
internal fun KeyframesImportButton(mode: KeyframesWorkspaceMode, importer: KeyframesImporter) {
    val devices by ViewportRepository.devices.collectAsState()
    val pickerTitle = stringResource(Res.string.keyframes_import_picker)
    val targetTitle = stringResource(Res.string.keyframes_import_target_title)

    Button(
        onClick = {
            importer.import(
                mode = mode,
                pickerTitle = pickerTitle,
                targetTitle = targetTitle,
            )
        },
        enabled = devices.isNotEmpty() && !importer.busy,
        variant = ButtonVariant.Ghost,
        size = ButtonSize.Small,
    ) {
        if (importer.busy) {
            Spinner(
                modifier = Modifier
                    .size(size = 16.dp),

                size = 16.dp,
            )
        } else {
            Icon(
                imageVector = Lucide.Upload,
                contentDescription = null,
                tint = workspaceToolbarContentColor(variant = ButtonVariant.Ghost),
                modifier = Modifier
                    .size(size = 16.dp)
            )
        }

        Text(
            text = stringResource(
                resource = if (importer.busy) {
                    Res.string.keyframes_import_busy
                } else {
                    Res.string.keyframes_import_action
                }
            )
        )
    }
}
