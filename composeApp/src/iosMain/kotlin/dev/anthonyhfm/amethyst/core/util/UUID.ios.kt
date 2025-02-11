package dev.anthonyhfm.amethyst.core.util

import platform.Foundation.NSUUID

actual fun UUID.randomUUID(): String {
    return NSUUID().UUIDString()
}