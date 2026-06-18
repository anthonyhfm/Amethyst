package dev.anthonyhfm.amethyst.workspace.ui.components

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object IosWorkspaceBridge {
    var onShowSettings: (() -> Unit)? = null
    var onOrientationChanged: ((Boolean) -> Unit)? = null
    var createLiquidGlassEffect: (() -> platform.UIKit.UIVisualEffect)? = null
    var createLiquidGlassContainerEffect: (() -> platform.UIKit.UIVisualEffect)? = null
    var createLiquidGlassButtonConfiguration: (() -> platform.UIKit.UIButtonConfiguration)? = null
}
