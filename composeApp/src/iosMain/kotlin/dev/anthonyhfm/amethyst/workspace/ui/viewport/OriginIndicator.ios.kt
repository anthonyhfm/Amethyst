package dev.anthonyhfm.amethyst.workspace.ui.viewport

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import dev.anthonyhfm.amethyst.workspace.ui.components.IosWorkspaceBridge
import platform.UIKit.*

@OptIn(ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun OriginIndicator(
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            val effect = IosWorkspaceBridge.createLiquidGlassEffect?.invoke()
            val glassView = if (effect != null) {
                UIVisualEffectView(effect = effect)
            } else {
                UIView().apply {
                    backgroundColor = UIColor.colorWithRed(0.157, 0.173, 0.204, 1.0) // fallback #282C34
                }
            }

            glassView.clipsToBounds = true
            glassView.layer.cornerRadius = 24.0 // 48.dp size / 2

            val label = UILabel().apply {
                text = "0,0"
                textColor = UIColor.secondaryLabelColor
                textAlignment = NSTextAlignmentCenter
                font = UIFont.systemFontOfSize(12.0, UIFontWeightMedium)
            }

            val container = (glassView as? UIVisualEffectView)?.contentView ?: glassView
            container.addSubview(label)

            label.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activateConstraints(
                listOf(
                    label.centerXAnchor.constraintEqualToAnchor(container.centerXAnchor),
                    label.centerYAnchor.constraintEqualToAnchor(container.centerYAnchor),
                )
            )

            glassView
        },
        modifier = modifier,
        properties = UIKitInteropProperties(
            placedAsOverlay = true,
        ),
    )
}
