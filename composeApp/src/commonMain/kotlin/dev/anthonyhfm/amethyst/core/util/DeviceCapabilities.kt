package dev.anthonyhfm.amethyst.core.util

/**
 * # DeviceCapabilities
 *
 * The DeviceCapabilities API is supposed to give a little more technical insight which device can do what things.
 * This is especially useful for mobile platforms where certain devices might not be supported due to performance or technical limitations.
 *
 * For example my iPhone 7 that I got specifically to test lower-end hardware performance might not be able to run certain CPU-intensive devices smoothly.
 */
data class DeviceCapabilities(
    val showFPSSettings: Boolean = true,
    val initialFPS: Int = 60,
    val showGradientSmoothnessSettings: Boolean = true,
    val lowRamUsageMode: Boolean = false,
)

expect fun getDeviceCapabilities(): DeviceCapabilities