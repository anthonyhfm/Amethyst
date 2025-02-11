package dev.anthonyhfm.amethyst.core.util

actual fun UUID.randomUUID(): String {
    return java.util.UUID.randomUUID().toString()
}