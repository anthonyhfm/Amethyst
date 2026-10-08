package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import dev.anthonyhfm.amethyst.settings.AppLocaleProvider
import dev.anthonyhfm.amethyst.ui.components.primitives.ScaleToFit
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadIdealised
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.ui.theme.ComposeAmethystTheme
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun launchpadForPicker(index: Int, interactive: Boolean): LaunchpadViewportElement = when (index) {
    0 -> ViewportLaunchpadPro(interactive = interactive)
    1 -> ViewportLaunchpadX(interactive = interactive)
    2 -> ViewportLaunchpadProMk3(interactive = interactive)
    3 -> ViewportLaunchpadMk2(interactive = interactive)
    4 -> ViewportLaunchpadIdealised(interactive = interactive)
    5 -> ViewportMystrix(interactive = interactive)
    6 -> ViewportMidiFighter64(interactive = interactive)
    else -> error("Unknown Launchpad picker index: $index")
}

@OptIn(ExperimentalComposeUiApi::class)
fun launchpadPreviewViewController(index: Int, darkMode: Boolean) = ComposeUIViewController(
    configure = {
        opaque = false
    }
) {
    val device = remember(index) {
        launchpadForPicker(index = index, interactive = false)
    }

    DisposableEffect(device) {
        onDispose { device.close() }
    }

    AppLocaleProvider {
        ComposeAmethystTheme(darkMode = darkMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScaleToFit {
                    device.Content()
                }
            }
        }
    }
}

fun isReplacingLaunchpadFromIosPicker(): Boolean = WorkspaceRepository.devicePickerReplacementId.value != null

fun dismissLaunchpadFromIosPicker() {
    WorkspaceRepository.closeDevicePicker()
}

fun addVirtualLaunchpadFromIosPicker(index: Int) {
    if (index !in 0..6) {
        return
    }

    val replacementId = WorkspaceRepository.devicePickerReplacementId.value
    CoroutineScope(context = Dispatchers.Main).launch {
        WorkspaceRepository.completeDevicePicker(
            element = launchpadForPicker(index = index, interactive = true),
            replacementId = replacementId,
        )
        WorkspaceRepository.closeDevicePicker()
    }
}
