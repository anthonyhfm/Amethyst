package dev.anthonyhfm.amethyst.core.util

actual fun getDeviceCapabilities(): DeviceCapabilities {
    // Conservative defaults for Android until we have device-specific profiling
    return DeviceCapabilities(
        showFPSSettings = true,
        initialFPS = 60,
        showGradientSmoothnessSettings = true,
        lowRamUsageMode = false
    )
}

