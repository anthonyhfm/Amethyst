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

    val lowRam = (isIphone && (iphoneGen ?: Int.MAX_VALUE) < 13) || (isIpad && !isIpadFpsAllowed(modelIdentifier))

    return DeviceCapabilities(
        showFPSSettings = showFps,
        initialFPS = 60,
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
    if (!identifier.startsWith("iPad")) return false

    // iPad Pro (2. Gen, 2017): iPad7,1–iPad7,4
    val isIpadPro2 = Regex("^iPad7,[1-4]$").matches(identifier)

    // iPad Pro (2018/2020): iPad8,1–iPad8,12
    val isIpadPro3Or4 = Regex("^iPad8,([1-9]|1[0-2])$").matches(identifier)

    // iPad Pro (2021, M1): iPad13,4–iPad13,11
    val isIpadPro5 = Regex("^iPad13,([4-9]|1[0-1])$").matches(identifier)

    // iPad Pro (2022, M2): iPad14,3–iPad14,6
    val isIpadPro6 = Regex("^iPad14,[3-6]$").matches(identifier)

    // iPad Air (4. Gen, 2020): iPad13,1–iPad13,2
    val isIpadAir4 = Regex("^iPad13,[1-2]$").matches(identifier)

    // iPad Air (5. Gen, 2022): iPad13,16–iPad13,17
    val isIpadAir5 = Regex("^iPad13,1[6-7]$").matches(identifier)

    // iPad Air (6. Gen, 2024): iPad14,8–iPad14,9
    val isIpadAir6 = Regex("^iPad14,[8-9]$").matches(identifier)

    return isIpadPro2 || isIpadPro3Or4 || isIpadPro5 || isIpadPro6 || isIpadAir4 || isIpadAir5 || isIpadAir6
}
