package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.*

@OptIn(ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun AddDeviceButton(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            UIButton.buttonWithType(UIButtonTypeSystem).apply {
                configuration = liquidGlassButtonConfiguration().apply {
                    title = "Add Device"
                    image = UIImage.systemImageNamed("plus")
                    imagePadding = 8.0
                    baseForegroundColor = UIColor.labelColor
                }
                addAction(
                    UIAction.actionWithHandler {
                        onClick()
                    },
                    forControlEvents = UIControlEventTouchUpInside,
                )
            }
        },
        modifier = modifier.height(50.dp),
        properties = UIKitInteropProperties(
            placedAsOverlay = true,
        ),
    )
}

private fun liquidGlassButtonConfiguration(): UIButtonConfiguration =
    IosWorkspaceBridge.createLiquidGlassButtonConfiguration?.invoke()
        ?: UIButtonConfiguration.borderedButtonConfiguration().apply {
            cornerStyle = UIButtonConfigurationCornerStyleCapsule
        }
