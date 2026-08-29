package dev.anthonyhfm.amethyst.desktop.utility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.awt.GraphicsConfiguration
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/**
 * Makes a Compose Desktop window refresh immediately when AWT assigns it to a
 * different display configuration.
 *
 * macOS can update a window's GraphicsConfiguration without resizing its
 * content component. Compose normally discovers the new density during paint,
 * but no paint is guaranteed for an abrupt display/scaling change. Forcing one
 * component-resize notification plus validation gives Compose a deterministic
 * opportunity to update its scene density and remeasure the UI.
 */
@Composable
fun RefreshOnDisplayChange(window: Window) {
    DisposableEffect(window) {
        var lastConfiguration = window.graphicsConfiguration.displayIdentity()
        var refreshScheduled = false

        fun scheduleRefreshIfNeeded() {
            val nextConfiguration = window.graphicsConfiguration.displayIdentity()
            if (nextConfiguration == lastConfiguration || refreshScheduled) return
            lastConfiguration = nextConfiguration
            refreshScheduled = true

            SwingUtilities.invokeLater {
                refreshScheduled = false
                if (!window.isDisplayable) return@invokeLater

                // ComposeContainer listens for this event on the native window.
                // A synthetic notification is safe: it does not mutate the window
                // bounds, but refreshes scene size/density before the next frame.
                window.dispatchEvent(ComponentEvent(window, ComponentEvent.COMPONENT_RESIZED))
                window.invalidate()
                window.validate()
                window.repaint()
            }
        }

        val componentListener = object : ComponentAdapter() {
            override fun componentMoved(event: ComponentEvent?) = scheduleRefreshIfNeeded()
            override fun componentResized(event: ComponentEvent?) = scheduleRefreshIfNeeded()
            override fun componentShown(event: ComponentEvent?) = scheduleRefreshIfNeeded()
        }
        val graphicsConfigurationListener = PropertyChangeListener { event: PropertyChangeEvent ->
            if (event.propertyName == "graphicsConfiguration") scheduleRefreshIfNeeded()
        }

        window.addComponentListener(componentListener)
        window.addPropertyChangeListener("graphicsConfiguration", graphicsConfigurationListener)

        onDispose {
            window.removeComponentListener(componentListener)
            window.removePropertyChangeListener("graphicsConfiguration", graphicsConfigurationListener)
        }
    }
}

private data class DisplayIdentity(
    val deviceId: String,
    val scaleX: Double,
    val scaleY: Double,
    val boundsX: Int,
    val boundsY: Int,
    val boundsWidth: Int,
    val boundsHeight: Int,
)

private fun GraphicsConfiguration?.displayIdentity(): DisplayIdentity? = this?.let {
    DisplayIdentity(
        deviceId = device.toString(),
        scaleX = defaultTransform.scaleX,
        scaleY = defaultTransform.scaleY,
        boundsX = bounds.x,
        boundsY = bounds.y,
        boundsWidth = bounds.width,
        boundsHeight = bounds.height,
    )
}
