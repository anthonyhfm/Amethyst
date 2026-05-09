package dev.anthonyhfm.amethyst.devices.effects.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.core.engine.heaven.RawLEDUpdate
import dev.anthonyhfm.amethyst.core.engine.heaven.isLit
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadMk2
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadPro
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadProMk3
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMidiFighter64
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportMystrix
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class PreviewChainDevice : GenericChainDevice<PreviewChainDeviceState>() {
    override val state = MutableStateFlow(PreviewChainDeviceState)

    private val signalBuffer = MutableStateFlow<Map<Pair<Int, Int>, Signal.LED>>(emptyMap())

    override fun signalEnter(n: List<Signal>) {
        val ledSignals = n.filterIsInstance<Signal.LED>()
        if (ledSignals.isNotEmpty()) {
            signalBuffer.update { current ->
                buildMap {
                    putAll(current)
                    ledSignals.forEach { signal ->
                        val key = Pair(signal.x, signal.y)
                        if (signal.color.isLit()) {
                            put(key, signal)
                        } else {
                            remove(key)
                        }
                    }
                }
            }
        }

        signalExit?.invoke(n)
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val isSelected = selections.any { it.selectionUUID == this.selectionUUID }
        val devices = Heaven.devices
        val signals by signalBuffer.collectAsState()

        val cardWidth = when {
            devices.size <= 1 -> 220.dp
            else -> 140.dp
        }

        ChainDeviceShell(
            title = "Preview",
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(cardWidth),
            titleBarModifier = LocalTitleBarModifier.current,
        ) {
            if (devices.isEmpty()) return@ChainDeviceShell

            if (devices.size == 1) {
                SingleDeviceView(original = devices.first(), signals = signals)
            } else {
                MultiDeviceView()
            }
        }
    }

    @Composable
    private fun SingleDeviceView(
        original: LaunchpadViewportElement,
        signals: Map<Pair<Int, Int>, Signal.LED>,
    ) {
        val previewViewport = remember(original.launchpadId) {
            createPreviewViewport(original).also { preview ->
                preview.position = original.position
                // Route pad interactions through this device's signalExit so the signal
                // enters the chain at the PreviewChainDevice's position (pre-effect triggering).
                preview.onCapturePad = { (down, x, y) ->
                    signalExit?.invoke(listOf(
                        Signal.LED(
                            origin = null,
                            x = x,
                            y = y,
                            color = if (down) Color.White else Color.Black,
                            layer = 0,
                        )
                    ))
                }
            }
        }

        LaunchedEffect(signals) {
            previewViewport.previewState.clear()
            previewViewport.previewState.sendToPreview(
                buildPreviewUpdates(signals.values.toList(), original)
            )
        }

        Box(
            modifier = Modifier
                .padding(8.dp)
                .shadow(elevation = 4.dp, shape = previewViewport.shape)
                .aspectRatio(1f)
        ) {
            previewViewport.Content()
        }
    }

    @Composable
    private fun MultiDeviceView() {
        val customMode = remember {
            PreviewWorkspaceMode(
                onPadInteraction = { down, x, y ->
                    signalExit?.invoke(listOf(
                        Signal.LED(
                            origin = null,
                            x = x,
                            y = y,
                            color = if (down) Color.White else Color.Black,
                            layer = 0,
                        )
                    ))
                }
            ).also { mode ->
                mode.modeClose = { WorkspaceRepository.switchToPreviousMode() }
            }
        }

        Button(
            onClick = {
                WorkspaceRepository.switchMode(customMode)
                customMode.wake()
            },
            variant = ButtonVariant.Default,
            size = ButtonSize.IconLarge,
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Open Preview",
                modifier = Modifier.size(36.dp),
                tint = Theme[colors][primaryForeground],
            )
        }
    }

    companion object : ChainDeviceFactory<PreviewChainDeviceState> {
        override val stateClass = PreviewChainDeviceState::class
        override val serializer = PreviewChainDeviceState.serializer()
        override fun create() = PreviewChainDevice()
    }
}

private fun createPreviewViewport(original: LaunchpadViewportElement): LaunchpadViewportElement =
    when (original) {
        is ViewportLaunchpadPro    -> ViewportLaunchpadPro()
        is ViewportLaunchpadMk2    -> ViewportLaunchpadMk2()
        is ViewportLaunchpadX      -> ViewportLaunchpadX()
        is ViewportLaunchpadProMk3 -> ViewportLaunchpadProMk3()
        is ViewportMystrix         -> ViewportMystrix()
        is ViewportMidiFighter64   -> ViewportMidiFighter64()
        else                       -> ViewportLaunchpadX()
    }

private fun buildPreviewUpdates(
    signals: List<Signal.LED>,
    device: LaunchpadViewportElement,
): List<RawLEDUpdate> {
    val layout = device.layout
    val posX = device.position.value.x.toInt()
    val posY = device.position.value.y.toInt()

    return signals.mapNotNull { signal ->
        val localX = signal.x - posX
        val localY = signal.y - posY

        if (localX !in 0 until layout.cols || localY !in 0 until layout.rows) {
            return@mapNotNull null
        }

        val previewX = localX + layout.offsetX
        val previewY = (layout.rows - 1 - localY) + layout.offsetY
        val index = previewX + previewY * 10

        if (index !in 0 until 100) return@mapNotNull null

        RawLEDUpdate(index = index, color = signal.color)
    }
}

@Serializable
data object PreviewChainDeviceState : DeviceState()
