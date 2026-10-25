package dev.anthonyhfm.amethyst.workspace.modes

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.launchpad_selection_cancel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MousePointer
import com.composables.icons.lucide.X
import com.composeunstyled.Icon
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportConfig
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportPanBoundsPolicy
import dev.anthonyhfm.amethyst.workspace.ui.viewport.ViewportState
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

class LaunchpadSelectionWorkspaceMode internal constructor(
    override val displayName: String,
) : WorkspaceMode() {
    override val claimMidiInputs: Boolean = true

    private val result = CompletableDeferred<String?>()
    private val viewportState = ViewportState()

    fun select(launchpad: LaunchpadViewportElement) {
        if (ViewportRepository.devices.value.any { it === launchpad }) {
            result.complete(value = launchpad.launchpadId)
        }
    }

    fun cancel() {
        result.complete(value = null)
    }

    internal suspend fun awaitSelection(): String? = result.await()

    override fun onDeactivate() {
        cancel()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val shouldCancel = event.key == Key.Escape ||
                (event.key == Key.W && (event.isCtrlPressed || event.isMetaPressed))
        if (shouldCancel && event.type == KeyEventType.KeyDown) {
            cancel()
            return true
        }
        return false
    }

    override fun onMidiInput(data: MidiInputData, offset: Offset) = Unit

    @Composable
    fun TopAppBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Lucide.MousePointer,
                contentDescription = null,
                tint = Theme[colors][foreground],
                modifier = Modifier
                    .size(size = 16.dp),
            )
            Text(text = displayName, color = Theme[colors][foreground])
            Spacer(
                modifier = Modifier
                    .weight(weight = 1f),
            )
            Button(
                onClick = { cancel() },
                variant = ButtonVariant.Outline,
                size = ButtonSize.Small,
            ) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = null,
                    tint = Theme[colors][foreground],
                    modifier = Modifier
                        .size(size = 16.dp),
                )
                Text(text = stringResource(Res.string.launchpad_selection_cancel))
            }
        }
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val devices by ViewportRepository.devices.collectAsState()

        LaunchedEffect(key1 = devices.isEmpty()) {
            if (devices.isEmpty()) {
                cancel()
            }
        }

        WorkspaceViewport(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            viewportKey = "workspace-launchpad-selection",
            viewportState = viewportState,
            elements = devices,
            config = ViewportConfig(
                minZoom = 0.1f,
                maxZoom = 2f,
                contentPadding = 64.dp,
                panBoundsPolicy = ViewportPanBoundsPolicy.ClampToContent(),
                showRemoteCursors = false,
            ),
            onLaunchpadPicked = { select(launchpad = it) },
        )
    }
}

suspend fun WorkspaceRepository.selectLaunchpad(
    title: String,
): LaunchpadViewportElement? = withContext(context = Dispatchers.Main.immediate) {
    val devices = ViewportRepository.devices.value
    if (devices.size <= 1) {
        return@withContext devices.singleOrNull()
    }

    val previousMode = mode.value
    val picker = LaunchpadSelectionWorkspaceMode(displayName = title)
    if (!isModeAvailable(mode = picker) || previousMode is LaunchpadSelectionWorkspaceMode) {
        return@withContext null
    }

    switchMode(mode = picker, undoable = false)
    try {
        val id = picker.awaitSelection()
        ViewportRepository.devices.value.firstOrNull { it.launchpadId == id }
    } finally {
        if (mode.value === picker) {
            switchMode(mode = previousMode, undoable = false)
        }
    }
}
