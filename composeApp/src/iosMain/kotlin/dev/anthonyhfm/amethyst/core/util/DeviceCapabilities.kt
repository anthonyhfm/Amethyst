package dev.anthonyhfm.amethyst.core.util

import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.UIKit.UIDevice
import platform.posix.getenv
import platform.posix.uname
import platform.posix.utsname

actual fun getDeviceCapabilities(): DeviceCapabilities {
    val modelIdentifier = getIosModelIdentifier()

    val (isIphone, iphoneGen) = parseIphoneModel(modelIdentifier)
    val isIpad = modelIdentifier.startsWith("iPad")

    val showFps = when {
        isIphone -> (iphoneGen ?: Int.MAX_VALUE) >= 13 // iPhone 12 (iPhone13,x) und neuer
        isIpad -> isIpadFpsAllowed(modelIdentifier)
        else -> true
    }

    val defaultFPS = when {
        (iphoneGen ?: Int.MAX_VALUE) >= 13 -> 90
        isIpad -> 120

        else -> 60
    }

    val lowRam = (isIphone && (iphoneGen ?: Int.MAX_VALUE) < 13) || (isIpad && !isIpadFpsAllowed(modelIdentifier))

    return DeviceCapabilities(
        showFPSSettings = showFps,
        initialFPS = defaultFPS,
        showGradientSmoothnessSettings = true,
        lowRamUsageMode = lowRam
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun getIosModelIdentifier(): String {
    memScoped {
        val uts = alloc<utsname>()
        val result = uname(uts.ptr as CValuesRef<utsname>?)
        if (result == 0) {
            val machine = uts.machine.toKString()
            if (machine.startsWith("iPhone") || machine.startsWith("iPad") || machine.startsWith("iPod")) {
                return machine
            }
        }
    }

    getenv("SIMULATOR_MODEL_IDENTIFIER")?.toKString()?.let { return it }

    return UIDevice.currentDevice.model
}

private fun parseIphoneModel(identifier: String): Pair<Boolean, Int?> {
    if (identifier.startsWith("iPhone")) {
        val rest = identifier.removePrefix("iPhone")
        val major = rest.substringBefore(',').toIntOrNull()
        return true to major
    }
    return false to null
}

private fun isIpadFpsAllowed(identifier: String): Boolean {
    return identifier.startsWith("iPad")
}
