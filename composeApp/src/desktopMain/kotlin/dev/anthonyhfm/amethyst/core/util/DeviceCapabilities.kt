package dev.anthonyhfm.amethyst.core.util

actual fun getDeviceCapabilities(): DeviceCapabilities {
    return DeviceCapabilities(
        showFPSSettings = true,
        initialFPS = 120,
        showGradientSmoothnessSettings = true,
        lowRamUsageMode = false
    )
}

